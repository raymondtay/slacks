package slacks.core.program

import scala.language.postfixOps

import providers.slack.algebra._
import providers.slack.models._
import slacks.core.config.{SlackTeamInfoConfig, SlackEmojiListConfig}

import akka.actor._
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}

/**
  * This is the Actor for the Slack Team Algebra; Basically its basically actor
  * implementations that goes out to the internet and fetches:
  * (a) team information carried by the token
  * (b) team emojis carried  by the token.
  *
  * Everything is dependent on the slack access token.
  * 
  * Captures all the errors/data found during the data retrieval
  *
  * @author Raymond Tay
  * @version 1.0
  */
import scala.concurrent.Future

case object GetTeamInfo
case object GetTeamEmoji

class SlackTeamInfoActor(teamInfoCfg : SlackTeamInfoConfig[String],
                         token : SlackAccessToken[String],
                         httpService : HttpService)(implicit aS: ActorSystem, aM: ActorMaterializer) extends Actor with ActorLogging {
  import cats._, data._, implicits._
  import org.atnos.eff._
  import org.atnos.eff.all._
  import org.atnos.eff.future._
  import org.atnos.eff.syntax.all._
  import org.atnos.eff.syntax.future._

  import akka.pattern.{pipe}
  import context.dispatcher

  implicit val http = Http(context.system)
  private val defaultUri = s"${teamInfoCfg.url}?token=${token.access_token}"
  private var localStorage : (TeamId, io.circe.Json) = ("", io.circe.Json.Null)

  type ParseJson[A] = io.circe.ParsingFailure Either A
  type ReaderResponseEntity[A] = Reader[ResponseEntity, A]
  type ReaderBytes[A] = Reader[ByteString, A]
  type WriteLog[A] = Writer[String, A]
  type Store[A] = State[(TeamId, io.circe.Json),A]
  type S1 = Fx.fx2[WriteLog, ReaderResponseEntity]
  type S2 = Fx.fx4[Store, ParseJson, ReaderBytes, WriteLog]

  val extractDataFromHttpStream : Eff[S1, Future[ByteString]] = for {
    entity <- ask[S1, ResponseEntity]
    _      <- tell[S1, String]("[Get-Team-Info-Actor] Collected the http entity.")
  } yield entity.dataBytes.runFold(ByteString(""))(_ ++ _)

  val decodeAsJson : Eff[S2, io.circe.Json] = {
    import io.circe.parser._
    import JsonCodecLens.getTeamIdValue
    for {
      datum <- ask[S2, ByteString]
       _    <- tell[S2,String]("[Get-Team-Info-Actor] Collected the json string from ctx.")
      json  <- fromEither[S2, io.circe.ParsingFailure, io.circe.Json](parse(datum.utf8String))
       _    <- tell[S2,String]("[Get-Team-Info-Actor] Collected the decoded json string.")
       _    <- put[S2, (TeamId, io.circe.Json)]((getTeamIdValue(json), json))
       _    <- modify[S2,(TeamId, io.circe.Json)]((s:(TeamId, io.circe.Json)) ⇒ {localStorage = s; localStorage})
     } yield json

  }

  override def preStart() = {
    httpService.makeSingleRequest.run(defaultUri).pipeTo(self)
  }

  def receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) ⇒
      import io.circe.parser.decode
      import JsonCodec._
      import scala.concurrent._,duration._

      implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext
      val possibleDatum : Throwable Either ByteString =
        Either.catchNonFatal{Await.result(extractDataFromHttpStream.runReader(entity).runWriterNoLog.run, 2 second)}

      val possibleJson : Either[Throwable, io.circe.Error Either io.circe.Json] =
      possibleDatum.map(datum ⇒ decodeAsJson.runReader(datum).runWriterNoLog.evalState(localStorage).runEither.run)

      possibleJson.joinRight match {
        case Left(error) ⇒ log.error("[Get-Team-Info-Actor] Error detected.")
        case Right(parsedJson) ⇒ log.info("[Get-Team-Info-Actor] OK.")
      }

    case resp @ HttpResponse(code, _, _, _) ⇒
      log.info("[Get-Team-Info-Actor] Request failed, response code: " + code)
      resp.discardEntityBytes()

    case GetTeamInfo ⇒ sender ! localStorage

    case StopAction ⇒ context stop self
  }

}

class SlackEmojiListActor(emojiListCfg : SlackEmojiListConfig[String],
                          token : SlackAccessToken[String],
                          httpService : HttpService)(implicit aS: ActorSystem, aM: ActorMaterializer) extends Actor with ActorLogging {
  import cats._, data._, implicits._
  import org.atnos.eff._
  import org.atnos.eff.all._
  import org.atnos.eff.future._
  import org.atnos.eff.syntax.all._
  import org.atnos.eff.syntax.future._

  import akka.pattern.{pipe}
  import context.dispatcher

  implicit val http = Http(context.system)
  private val defaultUri = s"${emojiListCfg.url}?token=${token.access_token}"
  private var localStorage : io.circe.Json = io.circe.Json.Null

  type ParseJson[A] = io.circe.ParsingFailure Either A
  type ReaderResponseEntity[A] = Reader[ResponseEntity, A]
  type ReaderBytes[A] = Reader[ByteString, A]
  type WriteLog[A] = Writer[String, A]
  type Store[A] = State[io.circe.Json,A]
  type S1 = Fx.fx2[WriteLog, ReaderResponseEntity]
  type S2 = Fx.fx4[Store, ParseJson, ReaderBytes, WriteLog]

  val extractDataFromHttpStream : Eff[S1, Future[ByteString]] = for {
    entity <- ask[S1, ResponseEntity]
    _      <- tell[S1, String]("[Get-Emoji-List-Actor] Collected the http entity.")
  } yield entity.dataBytes.runFold(ByteString(""))(_ ++ _)

  val decodeAsJson : Eff[S2, io.circe.Json] = {
    import io.circe.parser._
    for {
      datum <- ask[S2, ByteString]
       _    <- tell[S2,String]("[Get-Emoji-List-Actor] Collected the json string from ctx.")
      json  <- fromEither[S2, io.circe.ParsingFailure, io.circe.Json](parse(datum.utf8String))
       _    <- tell[S2,String]("[Get-Emoji-List-Actor] Collected the decoded json string.")
       _    <- put[S2, io.circe.Json](json)
       _    <- modify[S2,io.circe.Json]((s:io.circe.Json) ⇒ {localStorage = s; localStorage})
    } yield json

  }

  override def preStart() = {
    httpService.makeSingleRequest.run(defaultUri).pipeTo(self)
  }

  def receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) ⇒
      import io.circe.parser.decode
      import JsonCodec._
      import scala.concurrent._,duration._

      implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext
      val possibleDatum : Throwable Either ByteString =
        Either.catchNonFatal{Await.result(extractDataFromHttpStream.runReader(entity).runWriterNoLog.run, 2 second)}

      val possibleJson : Either[Throwable, io.circe.Error Either io.circe.Json] =
      possibleDatum.map(datum ⇒ decodeAsJson.runReader(datum).runWriterNoLog.evalState(localStorage).runEither.run)

      possibleJson.joinRight match {
        case Left(error) ⇒ log.error("[Get-Emoji-List-Actor] Error detected.")
        case Right(parsedJson) ⇒ log.info("[Get-Emoji-List-Actor] OK.")
      }

    case resp @ HttpResponse(code, _, _, _) ⇒
      log.info("[Get-Emoji-List-Actor] Request failed, response code: " + code)
      resp.discardEntityBytes()

    case GetTeamEmoji ⇒ sender ! localStorage

    case StopAction ⇒ context stop self
  }

}
