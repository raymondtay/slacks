package slacks.core.program

/**
  * This is the interpreter for the Slack Channel Algebra
  * @author Raymond Tay
  * @version 1.0
  */

import providers.slack.algebra._
import slacks.core.config.SlackChannelConfig

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}
import providers.slack.models._

object ChannelsInterpreter {

  import cats._, data._, implicits._
  import org.atnos.eff._
  import org.atnos.eff.all._
  import org.atnos.eff.future._
  import org.atnos.eff.syntax.all._
  import org.atnos.eff.syntax.future._

  import Channels._


  private def getChannelsFromSlack(cfg: SlackChannelConfig[String], 
                                   token: SlackAccessToken[String],
                                   httpService : HttpService)(implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val actor = actorSystem.actorOf(Props(new SlackChannelActor(cfg, token, httpService)), "slack-channel-actor")
    implicit val timeout = Timeout(cfg.timeout seconds)
    // TODO: Once Eff-Monad upgrades to allow waitFor, retryUntil, we will take
    // this abomination out.
    // Rationale for allowing the sleep to occur is because the ask i.e. ? will
    // occur before the http-request which would return a None.
    Thread.sleep(2000)
    futureDelay[Stack, Option[SlackChannelData]](Await.result((actor ? GetData).mapTo[Option[SlackChannelData]], timeout.duration))
  }
 
  /** 
    * Obtain the channel listing from Slack based on the token,
    * the process will handle the pagination mechanism embedded in Slack.
    *
    * TODO : Implement the pagination model.
    *
    * @param cfg configuration
    * @param token slack token
    * @param httpService 
    */
  def getChannelList(cfg: SlackChannelConfig[String], 
                     httpService : HttpService)(implicit actorSystem: ActorSystem, actorMat: ActorMaterializer) : Eff[Stack, Option[SlackChannelData]] = for {
    token    <- ask[Stack, SlackAccessToken[String]]
    _        <- tell[Stack, String]("Slack access token retrieved.")
    channels <- getChannelsFromSlack(cfg, token, httpService)   
    _        <- tell[Stack, String]("Slack channel(s) info retrieved.")
  } yield channels

}
