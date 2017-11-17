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

case object GetData
case class GetChannels(curr: List[SlackChannel], isAll: Boolean)

class SlackChannelActor(cfg : SlackChannelConfig[String],
                        token : SlackAccessToken[String],
                        httpService : HttpService)(implicit aS: ActorSystem, aM: ActorMaterializer) extends Actor with ActorLogging {

  import akka.pattern.{ask, pipe}
  import context.dispatcher

  implicit val http = Http(context.system)
  private var channelData : Option[SlackChannelData] = None

  override def preStart() = {
    val _uri = s"${cfg.url}?token=${token.access_token}"
    httpService.makeSingleRequest.run(_uri).pipeTo(self)
  }

  def receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) ⇒
      import io.circe.parser.decode
      import JsonCodec._
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body ⇒
        log.debug("[Slack-channel] Got response, body: " + body.utf8String)
        decode[SlackChannelData](body.utf8String) match {
          case Left(error) ⇒ log.error(s"[Slack-Channel] Json is not in the expected format.")
          case Right(parsedData) ⇒ 
            log.info(s"[Slack-Channel] Json parsed OK.")
            channelData = Some(parsedData)
        }
      }

    case resp @ HttpResponse(code, _, _, _) ⇒
      log.info("Request failed, response code: " + code)
      resp.discardEntityBytes()

    case GetData ⇒ 
      sender ! channelData

    case StopAction ⇒ context stop self
  }

}

