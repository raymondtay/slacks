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
  * @param blacklistCfg  the configuration object that holds the blacklisted message types
  * @param token the slack access token
  * @param httpService
  */

case class SievedMessages(
  botMessages : List[BotAttachmentMessage],
  userAttachmentMessages: List[UserAttachmentMessage],
  userFileShareMessages : List[UserFileShareMessage],
  fileCommentMessages : List[FileComment],
  whitelistedMessages : List[io.circe.Json]
) extends Serializable

case object GetConversationHistory

class SlackConversationHistoryActor(channelId: ChannelId,
                                    cfg : SlackChannelReadConfig[String],
                                    blConfig : slacks.core.config.SlackBlacklistMessageForUserMentions,
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

  import providers.slack.algebra.Messages

  type ReaderResponseEntity[A] = Reader[ResponseEntity, A]
  type ReaderBytes[A] = Reader[ByteString, A]
  type WriteLog[A] = Writer[String, A]
  type Store[A] = State[SievedMessages,A]
  type S1 = Fx.fx2[WriteLog, ReaderResponseEntity]
  type S2 = Fx.fx5[Store, ReaderBytes, WriteLog, List, Option]

  implicit val http = Http(context.system)
  private val defaultUri = s"${cfg.url}?channel=${channelId}&token=${Monoid[String].combine(token.access_token.prefix, token.access_token.value)}&limit=1000"
  private def continuationUri = (cursor:String) ⇒ defaultUri + s"&limit=1000&cursor=${cursor}"
  private var localStorage : SievedMessages = SievedMessages(Nil, Nil, Nil, Nil, Nil)
  private val cursorState : Cursor = Cursor("")

  override def preStart() = {
    httpService.makeSingleRequest.run(defaultUri).pipeTo(self)
  }

  // collect the entire json data prior to processing.
  val extractDataFromHttpStream : Eff[S1, Future[ByteString]] = for {
    entity <- ask[S1, ResponseEntity]
    _      <- tell[S1, String]("[Get-Channel-History-Actor] Collected the http entity.")
  } yield entity.dataBytes.runFold(ByteString(""))(_ ++ _)

  /**
    * Using Scala Lens, we sieve out the bot-specific data
    * bearing in mind that bot messages carrying attachments might have the
    * following condition:
    * (a) if "reactions" is present then its either an empty [] or [value,
    *     value...] and not to mention that "subtype" is absent too. That's
    *     confusing to say the least....
    * (b) "reactions" can be absent
    *
    * @param json messages from slack
    * @return all messages of type 'bot_message'
    */
  val findAllBotMessages : Kleisli[List, io.circe.Json, BotAttachmentMessage] = Kleisli{ (json: io.circe.Json) ⇒
    import JsonCodecLens._
    val botMessages =
      root.messages.each.filter{ (j: io.circe.Json) ⇒ Applicative[Id].map2(isMessagePresentNMatched(j), isSubtypePresentNMatched("bot_message")(j))(_ && _) }.obj.getAll(json)

    botMessages.map{
      message ⇒
        val messageJ : io.circe.Json = Json.fromJsonObject(message)

        Applicative[Id].map10(
          getMessageValue(messageJ),
          getSubtypeMessageValue(messageJ),
          getUsernameValue(messageJ),
          getBotIdValue(messageJ),
          getTextValue(messageJ),
          getAttachments(messageJ),
          getTimestampValue(messageJ),
          extractBotReactions(message),
          extractBotReplies(message),
          Messages.findUserMentions(messageJ))(BotAttachmentMessage.apply)
    }
  }

  /**
    * Locates all messages sent by regular slack users (i.e. non-bots) and has
    * no json field: 'subtype' present
    * @param json slack messages
    * @return container of user attachment message if any else an empty container
    */
  val findAllUserAttachmentMessages : Kleisli[List, io.circe.Json, UserAttachmentMessage] = Kleisli{ (json : io.circe.Json) ⇒
    import JsonCodecLens._
    val userMessagesWithAttachments =
      root.messages.each.filter{ (j: io.circe.Json) ⇒
        Applicative[Id].map4(isMessagePresentNMatched(j), isAttachmentsFieldPresent(j), !isBotIdFieldPresent(j), !isSubtypeFieldPresent(j))(_ && _ && _ && _)
      }.obj.getAll(json)

    userMessagesWithAttachments.map{
      message ⇒
        val messageJ : io.circe.Json = Json.fromJsonObject(message)
        Applicative[Id].map8(
          getMessageValue(messageJ),
          getUserIdValue(messageJ),
          getTextValue(messageJ),
          extractUserAttachments(message),
          getTimestampValue(messageJ),
          extractUserReactions(message),
          extractUserReplies(message),
          Messages.findUserMentions(messageJ))(UserAttachmentMessage.apply)
    }
  }

  /**
    * Parses the comments in the json structure looking for the matching file comments
    * @param fileId each message of `file_share` has a file's id associated
    * @param json message where subtype is `file_comment`
    * @return a container of comments associated with the given file's id
    */
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

  /**
    * Locate all 'file_share' messages that are shared by regular slack users (i.e. non-bots)
    * @param json slack messages
    * @return a container of UserFileShareMessages or an empty container
    */
  val findAllSharedFileContentOfNonBotUsers : Kleisli[List, io.circe.Json, UserFileShareMessage] = Kleisli{ (json: io.circe.Json) ⇒
    import JsonCodecLens._
    val sharedFileMessages =
      root.messages.each.filter{ (j: io.circe.Json) ⇒
        Applicative[Id].map4(isMessagePresentNMatched(j),
                             isSubtypePresentNMatched("file_share")(j),
                             isFileFieldPresent(j),
                             isUsernameFieldPresent(j))(_ && _ && _ && _)
      }.obj.getAll(json)

    import slacks.core.parser.UserMentions

    sharedFileMessages.map{
      fileMessage ⇒
        val j : io.circe.Json = Json.fromJsonObject(fileMessage)
        val userFile : UserFile = Applicative[Id].map15(
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
          getFileModeValue(j),
          getFileThumb360Value(j),
          getFileThumbPDFValue(j),
          getFileThumbVideoValue(j))(UserFile.apply)

        Applicative[Id].map10(getMessageValue(j),
                              getSubtypeMessageValue(j),
                              getTextValue(j),
                              userFile,
                              Messages.getFileInitialCommentInFileShareMessage(j).
                                fold(getFileComments(getFileIdValue(j))(json))
                                    (fileComment ⇒ getFileComments(getFileIdValue(j))(json) :+ fileComment),
                              if (!Messages.extractFileShareUserMentions(j).isEmpty) getFileInitialCommentValue(j).fold("")(x ⇒ x) else "",
                              getUserIdValue(j),
                              getBotIdValue(j),
                              getTimestampValue(j),
                              Messages.findUserMentions(j))(UserFileShareMessage.apply)
    }
  }

  /**
    * Extract the bot's "replies" from the json object
    * @param json 
    * @return empty container or a container of bot replies
    */
  val extractBotReplies : Kleisli[List, io.circe.JsonObject, Reply] = Kleisli{ (o: io.circe.JsonObject) ⇒
    import JsonCodec.slackReplyDec
    val json : io.circe.Json = Json.fromJsonObject(o)
    root.replies.arr.getOption(json) match {
      case Some(xs : Vector[io.circe.Json]) ⇒ xs.map(x ⇒ x.as[Reply].getOrElse(Reply("",""))).toList
      case None ⇒ List.empty[Reply]
    }
  }
  val extractUserReplies = extractBotReplies

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

  /**
    * Mines the `file_comment` messages looking for what makes a notable message.
    * @param json the root json object
    */
  val findNotableMessagesInFileComments = Kleisli{ (json: io.circe.Json) ⇒
    import JsonCodecLens._
    val fileCommentMessages =
      root.messages.each.filter{ (j: io.circe.Json) ⇒
        Applicative[Id].map2(isMessagePresentNMatched(j), isSubtypePresentNMatched("file_comment")(j))(_ && _)
      }.obj.getAll(json)

    fileCommentMessages.map{
      message ⇒
        val messageJ : io.circe.Json = Json.fromJsonObject(message)
        Messages.getUserMentionsInFileComments(messageJ)
    }
  }

  /**
    * Discover all user-mentions in the stockpile of json objects and to
    * prevent us from iterating through message types where we have already
    * processed before, we make use of `messageTypesToBeExcluded` (see Slack's
    * API message types for details.)
    * @param messageTypesToBeExcluded  e.g. ["file_share", "file_comment", "bot_message"]
    * @param blConfig configuration containing all blacklisted message types
    * @param json json object
    * @return an empty container or a container of jsons with a extra field named 'mentions'
    */
  def findNotableMessagesInWhiteListedSlackMessages(messageTypesToBeExcluded : List[String],
                                                    blacklistedMessages : slacks.core.config.SlackBlacklistMessageForUserMentions) = Kleisli{ (json: io.circe.Json) ⇒
    import JsonCodecLens._

    val wlMessages =
      root.messages.each.filter{ (j: io.circe.Json) ⇒
        Applicative[Id].map2(isMessagePresentNMatched(j), ! messageTypesToBeExcluded.contains(getSubtypeMessageValue(j)))(_ && _)
      }.obj.getAll(json)

    val transformedJsons =
      wlMessages.map{
        message ⇒
          val messageJ : io.circe.Json = Json.fromJsonObject(message)
          Messages.findUserMentions(messageJ) match {
            case Nil          ⇒ messageJ
            case usermentions ⇒ Messages.inject("mentions")(Json.arr(usermentions.map(Json.fromString(_)):_*)).run(messageJ)
          }
      }
    transformedJsons
  }

  //
  // Decode the JSON structure and sieve for data we are interested in
  // and the local state is updated while we push on with the data.
  //
  def sieveJsonAndNextCursor : Eff[S2, Option[String]] = {
    val excludedMessageTypes = List("file_share", "file_comment", "bot_message")

    for {
      datum            ← ask[S2, ByteString]
       _               ← tell[S2, String]("[Get-Channel-History-Actor] Collected the json data from ctx.")
      json             ← values[S2, List[io.circe.Json]](parse(datum.utf8String).getOrElse(Json.Null) :: Nil)
       _               ← tell[S2, String]("[Get-Channel-History-Actor] Converted the data to a json string.")
      botData          ← values[S2, List[BotAttachmentMessage]](findAllBotMessages(json head))
       _               ← tell[S2, String]("[Get-Channel-History-Actor] Processed json data for bot messages.")
      userAttData      ← values[S2, List[UserAttachmentMessage]](findAllUserAttachmentMessages(json head))
       _               ← tell[S2, String]("[Get-Channel-History-Actor] Processed json data for user attachment messages.")
      userFSData       ← values[S2, List[UserFileShareMessage]](findAllSharedFileContentOfNonBotUsers(json head))
       _               ← tell[S2, String]("[Get-Channel-History-Actor] Processed json data for file-share messages.")
      fileCommentData  ← values[S2, List[FileComment]](findNotableMessagesInFileComments(json head))
       _               ← tell[S2, String]("[Get-Channel-History-Actor] Processed json data for file-comment messages.")
      rest             ← values[S2, List[io.circe.Json]](findNotableMessagesInWhiteListedSlackMessages(excludedMessageTypes, blConfig)(json head))
       _               ← tell[S2, String](s"[Get-Channel-History-Actor] Processed json data for message types not in ${excludedMessageTypes.mkString(",")}.")
      cursor           ← fromOption[S2, String](getNextPage(json head))
       _               ← tell[S2, String]("[Get-Channel-History-Actor] Processed json data for next-cursor.")
       _               ← modify[S2, SievedMessages]((m:SievedMessages) ⇒ 
                     {localStorage =
                        m.copy(botMessages            = m.botMessages ++ botData,
                               userAttachmentMessages = m.userAttachmentMessages ++ userAttData,
                               userFileShareMessages  = m.userFileShareMessages ++ userFSData,
                               fileCommentMessages    = m.fileCommentMessages ++ fileCommentData,
                               whitelistedMessages    = m.whitelistedMessages ++ rest); localStorage})
    } yield cursor.some

  }

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

