package slacks.core.program

import providers.slack.algebra._
import providers.slack.models._
import slacks.core.config.SlackChannelConfig

import akka.actor._
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}

/**
  * This is the Actor for the Slack Channel Algebra
  * 
  * Captures all the errors/data found during the data retrieval
  *
  * @author Raymond Tay
  * @version 1.0
  */
import scala.concurrent.Future
case object GetData
case class Storage(xs: List[SlackChannel]) 

class SlackChannelActor(cfg : SlackChannelConfig[String],
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
  private val defaultUri = s"${cfg.url}?token=${token.access_token}&limit=20"
  private def continuationUri = (cursor:String) => defaultUri + s"&limit=20&cursor=${cursor}"
  private var localStorage : Storage = Storage(Nil)

  type DecodeJson[A] = io.circe.Error Either A
  type ReaderResponseEntity[A] = Reader[ResponseEntity, A]
  type ReaderBytes[A] = Reader[ByteString, A]
  type WriteLog[A] = Writer[String, A]
  type Store[A] = State[Storage,A]
  type S1 = Fx.fx2[WriteLog, ReaderResponseEntity]
  type S2 = Fx.fx4[Store, DecodeJson, ReaderBytes, WriteLog]

  val extractDataFromHttpStream : Eff[S1, Future[ByteString]] = for {
    entity <- ask[S1, ResponseEntity]
    _      <- tell[S1, String]("Collected the http entity.")
  } yield entity.dataBytes.runFold(ByteString(""))(_ ++ _)

  val decodeJsonNUpdateState : Eff[S2, (SlackChannelData, Storage)] = {
    import io.circe.parser.decode
    import JsonCodec._
    for {
     datum <- ask[S2, ByteString]
      _    <- tell[S2,String]("Collected the json string from ctx.")
     json  <- fromEither[S2, io.circe.Error, SlackChannelData](decode[SlackChannelData](datum.utf8String)) 
      _    <- tell[S2,String]("Collected the decoded json string.")
      a    <- get[S2, Storage]
      _    <- put[S2,Storage](a.copy(xs = a.xs ++ json.channels))
      b    <- get[S2, Storage]
    } yield (json, b)

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

      val possibleJson : Either[Throwable, io.circe.Error Either (SlackChannelData,Storage)] = 
      possibleDatum.map(datum ⇒ decodeJsonNUpdateState.runReader(datum).runWriterNoLog.evalState(localStorage).runEither.run) 

      possibleJson.joinRight match {
        case Left(error) ⇒ log.error("[Slack-channel] Error detected.")
        case Right((channelJson, storage)) ⇒ 
          localStorage = storage
          log.debug(s"[local-storage] ${localStorage.xs.size}")
          if (!channelJson.response_metadata.get.next_cursor.isEmpty)
            httpService.makeSingleRequest.run(continuationUri(channelJson.response_metadata.get.next_cursor)).pipeTo(self)
      }

    case resp @ HttpResponse(code, _, _, _) ⇒
      log.info("Request failed, response code: " + code)
      resp.discardEntityBytes()

    case GetData ⇒ 
      sender ! localStorage

    case StopAction ⇒ context stop self
  }

}

