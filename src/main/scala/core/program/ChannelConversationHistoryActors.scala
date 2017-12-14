package slacks.core.program

import providers.slack.algebra._
import providers.slack.models._
import slacks.core.config.SlackChannelReadRepliesConfig

import akka.actor._
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}
import scala.concurrent.Future

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
)

case object GetConversationHistory

class SlackConversationHistoryActor(channelId: ChannelId,
                                    cfg : SlackChannelReadRepliesConfig[String],
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
    val botMessages =
      root.messages.each.filter{ (j: io.circe.Json) ⇒
        val r  : Boolean = root.`type`.string.getOption(j) != None && root.`type`.string.exist(_ == "message")(j)
        val r2 : Boolean = root.bot_id.string.getOption(j) != None && root.bot_id.string.exist(_ != "")(j)
        val r3 : Boolean = root.attachments.arr.getOption(j) != None
        r && r2 && r3
      }.obj.getAll(json)

    botMessages.map{
      message ⇒
        val messageJ : io.circe.Json = Json.fromJsonObject(message)
        val userId    = root.user.string.getOption(messageJ).getOrElse("empty-user-id")
        val botId     = root.bot_id.string.getOption(messageJ).getOrElse("empty-bot-id")
        val `type`    = root.`type`.string.getOption(messageJ).getOrElse("empty-message-type")
        val txt       = root.text.string.getOption(messageJ).getOrElse("empty-text")
        val timestamp = root.ts.string.getOption(messageJ).getOrElse("empty-timestamp")
        BotAttachmentMessage(`type`, user = userId, bot_id = botId, text = txt, ts = timestamp, attachments = extractBotAttachments(message), reactions = extractBotReactions(message), replies = extractBotReplies(message))
    }
  }

  // Locates all messages sent by regular slack users (i.e. non-bots) 
  val findAllUserAttachmentMessages : Kleisli[List, io.circe.Json, UserAttachmentMessage] = Kleisli{ (json : io.circe.Json) ⇒
    val userMessagesWithAttachments = 
      root.messages.each.filter{ (j: io.circe.Json) ⇒
        val r  = root.`type`.string.getOption(j) != None && root.`type`.string.exist(_ == "message")(j)
        val r2 = root.subtype.string.getOption(j) == None
        val r3 = root.attachments.arr.getOption(j) != None
        val r4 = root.bot_id.string.getOption(j) == None
        r && r2 && r3 && r4
      }.obj.getAll(json)

    userMessagesWithAttachments.map{
      message ⇒
        val messageJ : io.circe.Json = Json.fromJsonObject(message)
        val userId    = root.user.string.getOption(messageJ).getOrElse("empty-user-id")
        val botId     = "empty-bot-id"
        val `type`    = root.`type`.string.getOption(messageJ).getOrElse("empty-message-type")
        val txt       = root.text.string.getOption(messageJ).getOrElse("empty-text")
        val timestamp = root.ts.string.getOption(messageJ).getOrElse("empty-timestamp")
        UserAttachmentMessage(`type`, user = userId, bot_id = botId, text = txt, ts = timestamp, attachments = extractUserAttachments(message), reactions = extractUserReactions(message), replies = extractUserReplies(message))
    }
  }

  // Parses the comments in the json structure looking for the matching file
  // comments
  def getFileComments(fileId: String) : Kleisli[List, io.circe.Json, UserFileComment] = Kleisli{ (json: io.circe.Json) ⇒
    val matchedCommentsForFile = 
      root.messages.each.filter{ (j: io.circe.Json) ⇒
        val r  = root.`type`.string.getOption(j) != None && root.`type`.string.exist(_ == "message")(j)
        val r2 = root.subtype.string.getOption(j) != None && root.subtype.string.exist(_ == "file_comment")(j)
        val r3 = root.file.obj.getOption(j) != None && root.file.id.string.exist(_ == fileId)(j)
        r && r2 && r3
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
    val sharedFileMessages = 
      root.messages.each.filter{ (j: io.circe.Json) ⇒
        val r  = root.`type`.string.getOption(j) != None && root.`type`.string.exist(_ == "message")(j)
        val r2 = root.subtype.string.getOption(j) != None && root.subtype.string.exist(_ == "file_share")(j)
        val r3 = root.file.obj.getOption(j) != None
        val r4 = root.username.string.getOption(j) != None && root.username.string.exist(_ != "")(j)
        r && r2 && r3 && r4
      }.obj.getAll(json)

    sharedFileMessages.map{ 
      fileMessage ⇒
        val messageJ : io.circe.Json = Json.fromJsonObject(fileMessage)
        val `type` = root.`type`.string.getOption(messageJ).getOrElse("empty-message-type")
        val subtype = root.subtype.string.getOption(messageJ).getOrElse("empty-message-subtype")
        val txt = root.text.string.getOption(messageJ).getOrElse("empty-text")
        val fileId = root.file.id.string.getOption(messageJ).getOrElse("empty-file-id")
        val created = root.file.created.long.getOption(messageJ).getOrElse(0L)
        val timestamp = root.file.timestamp.long.getOption(messageJ).getOrElse(0L)
        val fileName = root.file.name.string.getOption(messageJ).getOrElse("empty-file-name")
        val fileTitle = root.file.title.string.getOption(messageJ).getOrElse("empty-title")
        val fileType = root.file.filetype.string.getOption(messageJ).getOrElse("empty-filetype")
        val filePrettyType = root.file.pretty_type.string.getOption(messageJ).getOrElse("empty-pretty_type")
        val userId = root.user.string.getOption(messageJ).getOrElse("empty-user")
        val userName = root.username.string.getOption(messageJ).getOrElse("empty-username")
        val ts = root.ts.string.getOption(messageJ).getOrElse("empty-ts")
        val isExternal = root.file.is_external.boolean.getOption(messageJ).getOrElse(false)
        val thumb1024 = root.file.thumb_1024.string.getOption(messageJ).getOrElse("empty-thumb_1024")
        val permalink = root.file.permalink.string.getOption(messageJ).getOrElse("empty-permalink")
        val externalType = root.file.external_type.string.getOption(messageJ).getOrElse("empty-external_type")
        UserFileShareMessage(`type`, subtype, txt, fileId, created, timestamp, fileName, fileTitle, fileType, filePrettyType, userId, isExternal, externalType, userName, thumb1024, permalink, getFileComments(fileId)(json), ts)
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
  val extractUserAttachments : Kleisli[List, io.circe.JsonObject, UserAttachment] = Kleisli{ (o : io.circe.JsonObject) ⇒
    import JsonCodec.slackUserAttachmentDec
    val json : io.circe.Json = Json.fromJsonObject(o)
    root.attachments.arr.getOption(json) match {
      case Some(xs : Vector[io.circe.Json]) ⇒ xs.map(x ⇒ x.as[UserAttachment].getOrElse(UserAttachment("","","","","",0L,"","","",0,0))).toList
      case None ⇒ List.empty[UserAttachment]
    }
  }

  // Extract the "reactions" from the json object
  val extractBotReactions : Kleisli[List, io.circe.JsonObject, Reaction] = Kleisli{ (o : io.circe.JsonObject) ⇒
    import JsonCodec.slackReactionDec
    val json : io.circe.Json = Json.fromJsonObject(o)
    root.reactions.arr.getOption(json) match {
      case Some(xs:Vector[io.circe.Json]) ⇒ xs.map(x ⇒ x.as[Reaction].getOrElse(Reaction("",Nil,0L))).toList
      case None ⇒ List.empty[Reaction]
    }
  }
  val extractUserReactions = extractBotReactions

  // Using Lens, we sieve out the "file_share" messages sent by regular users
  // and the complication here is that we sieved through the entire content
  // looking for "file_comment" which relates to "file_share" messages.
  // TODO : continue fleshing out the implementation
  val findAllFileSharesWithReactions : Kleisli[List, io.circe.Json, io.circe.JsonObject] = Kleisli{ (json : io.circe.Json) ⇒
    root.messages.each.filter{ (j:io.circe.Json) ⇒
      val r  : Boolean = root.`type`.string.exist(_ == "message")(j)
      val r2 : Boolean = root.subtype.string.exist(_ == "file_share")(j)
      val r3 : Boolean = root.attachments.arr.getOption(j) != None
      r && r2 && r3
    }.obj.getAll(json)
  }

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
    case HttpResponse(StatusCodes.OK, headers, entity, _) ⇒
      log.info("[Get-Conversation-History-Actor] ")
      import JsonCodec._
      import scala.concurrent._,duration._

      implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext
      val possibleDatum : Throwable Either ByteString =
        Either.catchNonFatal{Await.result(extractDataFromHttpStream.runReader(entity).runWriterNoLog.run, 2 second)}

      val cursor : Option[String] =
      possibleDatum.toList.map(datum ⇒
        sieveJsonAndNextCursor.runReader(datum).runList.runWriterNoLog.evalState(localStorage).runOption.run head
      ).flatten.head

      cursor.isDefined && !cursor.get.isEmpty match {
        case false ⇒
          log.warning("[Get-Conversation-History-Actor] No more further JSON data detected from Http stream.")
        case true ⇒
          log.debug(s"[Get-Conversation-History-Actor][local-storage] bot-messages: ${localStorage.botMessages.size}, user-attachment-messages: ${localStorage.userAttachmentMessages.size}")
          log.info(s"[Get-Conversation-History-Actor] following the cursor to retrieve more data...")
          httpService.makeSingleRequest.run(continuationUri(cursor.get)).pipeTo(self)
      }

    case resp @ HttpResponse(code, _, _, _) ⇒
      log.info("[Get-Conversation-History-Actor] Request failed, response code: " + code)
      resp.discardEntityBytes()

    case GetConversationHistory ⇒ sender ! localStorage

    case StopAction ⇒ context stop self
  }

}

