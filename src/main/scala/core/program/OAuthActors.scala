package slacks.core.program

import providers.slack.algebra._
import slacks.core.config.SlackAccessConfig

import akka.actor._
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}

/**
  * This is the Actor for the Slack Algebra
  * @author Raymond Tay
  * @version 1.0
  */

trait HttpService {
  import cats.data.Kleisli, cats.implicits._
  import scala.concurrent.Future
  def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) : Kleisli[Future,String,HttpResponse]
}

class FakeHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  import ContentTypes._
  import scala.concurrent.Future
  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) = Kleisli{ 
    (_uri: String) ⇒ 
      println(s"FAKE SERVICE STARTED")
      Future.successful(
        HttpResponse(entity = HttpEntity(`text/plain(UTF-8)`, "test-token"))
        )
      /*
      Future.successful(
        HttpResponse(entity = HttpEntity(`application/json`, """{ "access_token": "test-token", "scope" : "read" }"""))
      )*/
  }
}

class RealHttpService extends HttpService {
  import cats.data.Kleisli, cats.implicits._
  override def makeSingleRequest(implicit http: HttpExt, akkaMat: ActorMaterializer) = Kleisli{ 
    (_uri: String) ⇒
      http.singleRequest(HttpRequest(uri = _uri))
  }
}

case object GetToken
case object StopAction
class SlackActor(cfg : SlackAccessConfig[String],
                 code : SlackCode,
                 credentials: SlackCredentials,
                 httpService : HttpService)(implicit aS: ActorSystem, aM: ActorMaterializer) extends Actor with ActorLogging {

  import akka.pattern.{ask, pipe}
  import context.dispatcher

  implicit val http = Http(context.system)
  private var token : Option[String] = None

  override def preStart() = {
    val _uri = s"${cfg.url}?client_id=${credentials._1}&client_secret=${credentials._2.get}&code=${code}"
    httpService.makeSingleRequest.run(_uri).pipeTo(self)
  }

  def receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) ⇒
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body ⇒
        log.info("Got response, body: " + body.utf8String)
        token = Some(body.utf8String)
      }

    case resp @ HttpResponse(code, _, _, _) ⇒
      log.info("Request failed, response code: " + code)
      resp.discardEntityBytes()

    case GetToken ⇒ 
      sender ! token

    case StopAction ⇒ context stop self
  }

}

