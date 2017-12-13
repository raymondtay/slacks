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

case class SievedMessages(messages : List[BotAttachmentMessage])
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
  private var localStorage : SievedMessages = SievedMessages(Nil)

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

  // Extract the bot's "replies" from the json object
  val extractBotReplies : Kleisli[List, io.circe.JsonObject, Reply] = Kleisli{ (o: io.circe.JsonObject) ⇒
    import JsonCodec.slackReplyDec
    val json : io.circe.Json = Json.fromJsonObject(o)
    root.replies.arr.getOption(json) match {
      case Some(xs : Vector[io.circe.Json]) ⇒ xs.map(x ⇒ x.as[Reply].getOrElse(Reply("",""))).toList
      case None ⇒ List.empty[Reply]
    }
  }

  // Extract the bot's "attachments" from the json object
  val extractBotAttachments : Kleisli[List, io.circe.JsonObject, Attachment] = Kleisli{ (o : io.circe.JsonObject) ⇒
    import JsonCodec.slackAttachmentDec
    val json : io.circe.Json = Json.fromJsonObject(o)
    root.attachments.arr.getOption(json) match {
      case Some(xs : Vector[io.circe.Json]) ⇒ xs.map(x ⇒ x.as[Attachment].getOrElse(Attachment("","","",0L,"",Nil))).toList
      case None ⇒ List.empty[Attachment]
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

  // Using Lens, we sieve out the non-bot related messages
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
    datum      <- ask[S2, ByteString]
     _         <- tell[S2, String]("[Get-Channel-History-Actor] Collected the json data from ctx.")
    json       <- values[S2, List[io.circe.Json]](parse(datum.utf8String).getOrElse(Json.Null) :: Nil)
     _         <- tell[S2, String]("[Get-Channel-History-Actor] Converted the data to a json string.")
    botData    <- values[S2, List[BotAttachmentMessage]](findAllBotMessages(json head))
     _         <- tell[S2, String]("[Get-Channel-History-Actor] Processed json data for bot messages.")
    //nonBotData <- values[S2, List[io.circe.JsonObject]](findAllFileSharesWithReactions(json head))
     _         <- tell[S2, String]("[Get-Channel-History-Actor] Processed json data for non-bot messages.")
    cursor     <- fromOption[S2, String](getNextPage(json head))
     _         <- tell[S2, String]("[Get-Channel-History-Actor] Processed json data for next-cursor.")
    _          <- modify[S2, SievedMessages]((m:SievedMessages) ⇒ {localStorage = m.copy(messages = m.messages ++ botData); localStorage})
    //_          <- modify[S2, SievedMessages]((m:SievedMessages) ⇒ {localStorage = m.copy(messages = m.messages ++ botData ++ nonBotData); localStorage})
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
          log.debug(s"[Get-Conversation-History-Actor][local-storage] ${localStorage.messages.size}")
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

