package slacks.core.program

import scala.language.postfixOps

import providers.slack.algebra._
import providers.slack.models._
import slacks.core.config.SlackChannelReadConfig

import akka.actor._
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}
import scala.concurrent._
import scala.concurrent.duration._

/**
  * This processing will be looking for two (i.e. 2) particular things
  * and they are: 
  * (a) all reactions and messages related to attachments posted by slack bots
  * (b) all reations and messages related to the attachments posted by regular
  *     human users
  *
  * @param channelid the id of the channel you are interested in
  * @param cfg  the configuration object
  * @param token the slack access token
  * @param httpService
  */

case class SievedMessages(
  botMessages : List[BotAttachmentMessage],
  userAttachmentMessages: List[UserAttachmentMessage],
  userFileShareMessages : List[UserFileShareMessage]
) extends Serializable

case object GetConversationHistory

class SlackConversationHistoryActor(channelId: ChannelId,
                                    cfg : SlackChannelReadConfig[String],
                                    token : SlackAccessToken[String],
                                    httpService : HttpService)(implicit aS: ActorSystem, aM: ActorMaterializer) extends Actor with ActorLogging {
  import cats._, data._, implicits._
  import io.circe._, io.circe.parser._
  import io.circe.optics.JsonPath._

  import org.atnos.eff._
  import org.atnos.eff.all._
  import org.atnos.eff.future._
  import org.atnos.eff.syntax.all._
  import org.atnos.eff.syntax.future._

  import akka.pattern.{pipe}
  import context.dispatcher

  type ReaderResponseEntity[A] = Reader[ResponseEntity, A]
  type ReaderBytes[A] = Reader[ByteString, A]
  type WriteLog[A] = Writer[String, A]
  type Store[A] = State[SievedMessages,A]
  type S1 = Fx.fx2[WriteLog, ReaderResponseEntity]
  type S2 = Fx.fx5[Store, ReaderBytes, WriteLog, List, Option]

  implicit val http = Http(context.system)
  private val defaultUri = s"${cfg.url}?channel=${channelId}&token=${token.access_token}&limit=1000"
  private def continuationUri = (cursor:String) ⇒ defaultUri + s"&limit=1000&cursor=${cursor}"
  private var localStorage : SievedMessages = SievedMessages(Nil, Nil, Nil)
  private val cursorState : Cursor = Cursor("")

  override def preStart() = {
    httpService.makeSingleRequest.run(defaultUri).pipeTo(self)
  }

  // collect the entire json data prior to processing.
  val extractDataFromHttpStream : Eff[S1, Future[ByteString]] = for {
    entity <- ask[S1, ResponseEntity]
    _      <- tell[S1, String]("[Get-Channel-History-Actor] Collected the http entity.")
  } yield entity.dataBytes.runFold(ByteString(""))(_ ++ _)

  // Using Scala Lens, we sieve out the bot-specific data
  // bearing in mind that bot messages carrying attachments might have the
  // following condition:
  // (a) if "reactions" is present then its either an empty [] or [value,
  //     value...] and not to mention that "subtype" is absent too. That's
  //     confusing to say the least....
  // (b) "reactions" can be absent
  //
  val findAllBotMessages : Kleisli[List, io.circe.Json, BotAttachmentMessage] = Kleisli{ (json: io.circe.Json) ⇒
    import JsonCodecLens._
    val botMessages =
      root.messages.each.filter{ (j: io.circe.Json) ⇒ isMessagePresentNMatched(json) }.obj.getAll(json)

    botMessages.map{
      message ⇒
        val messageJ : io.circe.Json = Json.fromJsonObject(message)
        Applicative[Id].map8( getMessageValue(messageJ), getUserIdValue(messageJ), getBotIdValue(messageJ), getTextValue(messageJ), Applicative[Id].pure(extractBotAttachments(message)), getTimestampValue(messageJ), Applicative[Id].pure(extractBotReactions(message)), Applicative[Id].pure(extractBotReplies(message)))(BotAttachmentMessage.apply)
    }
  }

  // Locates all messages sent by regular slack users (i.e. non-bots) 
  val findAllUserAttachmentMessages : Kleisli[List, io.circe.Json, UserAttachmentMessage] = Kleisli{ (json : io.circe.Json) ⇒
    import JsonCodecLens._
    val userMessagesWithAttachments =
      root.messages.each.filter{ (j: io.circe.Json) ⇒
        Applicative[Id].map4(isMessagePresentNMatched(j), isAttachmentsFieldPresent(j), !isBotIdFieldPresent(j), !isSubtypeFieldPresent(j))(_ && _ && _ && _)
      }.obj.getAll(json)

    userMessagesWithAttachments.map{
      message ⇒
        val messageJ : io.circe.Json = Json.fromJsonObject(message)
        Applicative[Id].map7(getMessageValue(messageJ), getUserIdValue(messageJ), getTextValue(messageJ), Applicative[Id].pure(extractUserAttachments(message)), getTimestampValue(messageJ), Applicative[Id].pure(extractUserReactions(message)), Applicative[Id].pure(extractUserReplies(message)))(UserAttachmentMessage.apply)
    }
  }

  // Parses the comments in the json structure looking for the matching file
  // comments
  def getFileComments(fileId: String) : Kleisli[List, io.circe.Json, UserFileComment] = Kleisli{ (json: io.circe.Json) ⇒
    import JsonCodecLens._
    val matchedCommentsForFile =
      root.messages.each.filter{ (j: io.circe.Json) ⇒
        Applicative[Id].map3(isMessagePresentNMatched(j), isSubtypePresentNMatched("file_comment")(j), isFileFieldWithMatchingFileId(fileId)(j))(_ && _ && _)
      }.obj.getAll(json)

    matchedCommentsForFile.map{
      message ⇒
        val messageJ : io.circe.Json = Json.fromJsonObject(message)
        import JsonCodec.slackUserFileCommentDec
        root.comment.obj.getOption(messageJ) match {
          case Some(x : io.circe.JsonObject) ⇒ Json.fromJsonObject(x).as[UserFileComment].getOrElse(UserFileComment("",0L,""))
          case None ⇒ UserFileComment("",0L,"")
        }
    }
  }

  // Locate all messages that are shared by regular slack users (i.e. non-bots)
  val findAllSharedFileContentByUsers : Kleisli[List, io.circe.Json, UserFileShareMessage] = Kleisli{ (json: io.circe.Json) ⇒
    import JsonCodecLens._
    val sharedFileMessages =
      root.messages.each.filter{ (j: io.circe.Json) ⇒
        Applicative[Id].map4(isMessagePresentNMatched(j), isSubtypePresentNMatched("file_share")(j), isFileFieldPresent(j), isUsernameFieldPresent(j))(_ && _ && _ && _)
      }.obj.getAll(json)

    sharedFileMessages.map{
      fileMessage ⇒
        val j : io.circe.Json = Json.fromJsonObject(fileMessage)
        val userFile : UserFile = Applicative[Id].map12(
          getFileTypeValue(j),
          getFileIdValue(j),
          getFileTitleValue(j),
          getFileUrlPrivateValue(j),
          getFileExternalTypeValue(j),
          getFileTimestampValue(j),
          getFilePrettyTypeValue(j),
          getFilenameValue(j),
          getFileMimeTypeValue(j),
          getPermalinkValue(j),
          getFileCreatedValue(j),
          getFileModeValue(j))(UserFile.apply)

        Applicative[Id].map8(getMessageValue(j), getSubtypeMessageValue(j),
                             getTextValue(j), userFile,
                             Applicative[Id].pure(getFileComments(getFileIdValue(j))(json)), getUserIdValue(j),
                             getBotIdValue(j), getTimestampValue(j))(UserFileShareMessage.apply)
    }
  }

  // Extract the bot's "replies" from the json object
  val extractBotReplies : Kleisli[List, io.circe.JsonObject, Reply] = Kleisli{ (o: io.circe.JsonObject) ⇒
    import JsonCodec.slackReplyDec
    val json : io.circe.Json = Json.fromJsonObject(o)
    root.replies.arr.getOption(json) match {
      case Some(xs : Vector[io.circe.Json]) ⇒ xs.map(x ⇒ x.as[Reply].getOrElse(Reply("",""))).toList
      case None ⇒ List.empty[Reply]
    }
  }
  val extractUserReplies = extractBotReplies

  // Extract the bot's "attachments" from the json object
  val extractBotAttachments : Kleisli[List, io.circe.JsonObject, BotAttachment] = Kleisli{ (o : io.circe.JsonObject) ⇒
    import JsonCodec.slackBotAttachmentDec
    val json : io.circe.Json = Json.fromJsonObject(o)
    root.attachments.arr.getOption(json) match {
      case Some(xs : Vector[io.circe.Json]) ⇒ xs.map(x ⇒ x.as[BotAttachment].getOrElse(BotAttachment("","","",0L,"",Nil))).toList
      case None ⇒ List.empty[BotAttachment]
    }
  }

  // Extract the user's "attachments" from the json object
  val extractUserAttachments : Kleisli[List, io.circe.JsonObject, io.circe.Json] = Kleisli{ (o : io.circe.JsonObject) ⇒
    val json : io.circe.Json = Json.fromJsonObject(o)
    root.attachments.arr.getOption(json) match {
      case Some(xs : Vector[io.circe.Json]) ⇒ xs.toList
      case None ⇒ List.empty[io.circe.Json]
    }
  }

  // Extract the "reactions" from the json object
  val extractBotReactions : Kleisli[List, io.circe.JsonObject, Reaction] = Kleisli{ (o : io.circe.JsonObject) ⇒
    import JsonCodec.slackReactionDec
    val json : io.circe.Json = Json.fromJsonObject(o)
    root.reactions.arr.getOption(json) match {
      case Some(xs:Vector[io.circe.Json]) ⇒ xs.map(x ⇒ x.as[Reaction].getOrElse(Reaction("",Nil))).toList
      case None ⇒ List.empty[Reaction]
    }
  }
  val extractUserReactions = extractBotReactions

  // Using lens, we look for the next cursor if we can find it (which would
  // return as a Some(x) else its a None)
  val getNextPage : Kleisli[Option,io.circe.Json,String] = Kleisli{ (json: io.circe.Json) ⇒
    root.response_metadata.next_cursor.string.getOption(json)
  }

  //
  // Decode the JSON structure and sieve for data we are interested in
  // and the local state is updated while we push on with the data.
  //
  val sieveJsonAndNextCursor : Eff[S2, Option[String]] = for {
    datum       <- ask[S2, ByteString]
     _          <- tell[S2, String]("[Get-Channel-History-Actor] Collected the json data from ctx.")
    json        <- values[S2, List[io.circe.Json]](parse(datum.utf8String).getOrElse(Json.Null) :: Nil)
     _          <- tell[S2, String]("[Get-Channel-History-Actor] Converted the data to a json string.")
    botData     <- values[S2, List[BotAttachmentMessage]](findAllBotMessages(json head))
     _          <- tell[S2, String]("[Get-Channel-History-Actor] Processed json data for bot messages.")
    userAttData <- values[S2, List[UserAttachmentMessage]](findAllUserAttachmentMessages(json head))
     _          <- tell[S2, String]("[Get-Channel-History-Actor] Processed json data for user attachment messages.")
    userFSData  <- values[S2, List[UserFileShareMessage]](findAllSharedFileContentByUsers(json head))
     _          <- tell[S2, String]("[Get-Channel-History-Actor] Processed json data for file-share messages.")
    cursor      <- fromOption[S2, String](getNextPage(json head))
     _          <- tell[S2, String]("[Get-Channel-History-Actor] Processed json data for next-cursor.")
     _          <- modify[S2, SievedMessages]((m:SievedMessages) ⇒ {localStorage = m.copy(botMessages = m.botMessages ++ botData, userAttachmentMessages = m.userAttachmentMessages ++ userAttData, userFileShareMessages = m.userFileShareMessages ++ userFSData); localStorage})
  } yield cursor.some

  def receive = {
    /* According to Slack Rate-limiting API, we should throttle our requests
     * based on the value of the embedded head 'Retry-After'
     **/
    case HttpResponse(StatusCodes.TooManyRequests, headers, entity, _) ⇒ 
      val rateLimit : HttpHeader = headers.filter(header ⇒ header.name() == "Retry-After").head
      context.system.scheduler.scheduleOnce(Integer.parseInt(rateLimit.value()).seconds)(httpService.makeSingleRequest.run(continuationUri(cursorState.getCursor)).pipeTo(self))

    /* if all goes well, the response would hit here */
    case HttpResponse(StatusCodes.OK, headers, entity, _) ⇒
      log.info("[Get-Conversation-History-Actor] ")
      import JsonCodec._
      import scala.concurrent._,duration._

      implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext
      val possibleDatum : Throwable Either ByteString =
        Either.catchNonFatal{Await.result(extractDataFromHttpStream.runReader(entity).runWriterNoLog.run, 9 second)}

      val cursor : Option[String] =
        possibleDatum.toList.map(datum ⇒
          sieveJsonAndNextCursor.runReader(datum).runList.runWriterNoLog.evalState(localStorage).runOption.run
        ).sequence match {
          case Some(xs) ⇒ xs.flatten match {
            case _cursor :: Nil ⇒ _cursor
            case _ ⇒ None
          }
        case None ⇒ None
        }

      cursor.isDefined && !cursor.get.isEmpty match {
        case false ⇒
          log.warning("[Get-Conversation-History-Actor] No more further JSON data detected from Http stream.")
        case true ⇒
          log.debug(s"[Get-Conversation-History-Actor][local-storage] bot-messages: ${localStorage.botMessages.size}, user-attachment-messages: ${localStorage.userAttachmentMessages.size}")
          log.info(s"[Get-Conversation-History-Actor] following the cursor :[${cursor.get}] to retrieve more data...")
          cursorState.updateCursor.run(cursor.get).value
          httpService.makeSingleRequest.run(continuationUri(cursorState.getCursor)).pipeTo(self)
      }

    case resp @ HttpResponse(code, _, _, _) ⇒
      log.info("[Get-Conversation-History-Actor] Request failed, response code: " + code)
      resp.discardEntityBytes()

    case GetConversationHistory ⇒ sender ! localStorage

    case StopAction ⇒ context stop self
  }

}

