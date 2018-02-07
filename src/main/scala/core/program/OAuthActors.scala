package slacks.core.program

import providers.slack.algebra._
import slacks.core.models.Token
import providers.slack.models.SlackAccessToken
import slacks.core.config.{SlackAccessConfig, SlackAuthScopeConfig}

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

case object GetToken
case object StopAction
class SlackActor(cfg : SlackAccessConfig[String],
                 code : SlackCode,
                 credentials: SlackCredentials,
                 httpService : HttpService)(implicit aS: ActorSystem, aM: ActorMaterializer) extends Actor with ActorLogging {

  import akka.pattern.{ask, pipe}
  import context.dispatcher

  implicit val http = Http(context.system)
  private var token : Option[SlackAccessToken[String]] = None

  override def preStart() = {
    val _uri = s"${cfg.url}?client_id=${credentials._1}&client_secret=${credentials._2.get}&code=${code}"
    httpService.makeSingleRequest.run(_uri).pipeTo(self)
  }

  def receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) ⇒
      import io.circe._, io.circe.parser.decode, io.circe.generic.auto._
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body ⇒
        log.info("Got response, body: " + body.utf8String)
        decode[SlackAccessToken[String]](body.utf8String) match {
          case Left(error) ⇒ log.error(s"[OAuth] Json is not in the expected format.")
          case Right(parsedToken) ⇒ 
            log.debug(s"[OAuth] Json parsed OK.")
            token = Some(parsedToken)
        }
      }

    case resp @ HttpResponse(code, _, _, _) ⇒
      log.info("Request failed, response code: " + code)
      resp.discardEntityBytes()

    case GetToken ⇒ 
      sender ! token

    case StopAction ⇒ context stop self
  }

}

class SlackScopeActor(cfg : SlackAuthScopeConfig[String], slackToken : Token, httpService : HttpService)
                     (implicit aS: ActorSystem, aM: ActorMaterializer) extends Actor with ActorLogging {

  import akka.pattern.{ask, pipe}
  import context.dispatcher
  import cats._, implicits._

  implicit val http = Http(context.system)
  private var token : Option[SlackAccessToken[String]] = None

  override def preStart() = {
    val _uri = s"${cfg.url}?token=${Monoid[String].combine(slackToken.prefix, slackToken.value)}"
    httpService.makeSingleRequest.run(_uri).pipeTo(self)
  }

  def receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) ⇒
      val scopesH : HttpHeader = headers.filter(header ⇒ header.name() == "x-oauth-scopes").head
      val scopes : Array[String] = scopesH.value().split(",")
      token = SlackAccessToken(slackToken, scopes.toList).some

    case resp @ HttpResponse(code, _, _, _) ⇒
      log.info("Request failed, response code: " + code)
      resp.discardEntityBytes()

    case GetToken ⇒
      sender ! token

    case StopAction ⇒ context stop self
  }

}
