package slacks.core.program

/**
  * This is the interpreter for the Slack Channel Algebra
  * @author Raymond Tay
  * @version 1.0
  */

import scala.language.postfixOps

import providers.slack.algebra._
import slacks.core.program.supervisor.SupervisorRestartN
import slacks.core.config.{SlackTeamInfoConfig, SlackEmojiListConfig}

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}
import providers.slack.models._

object TeamInfoInterpreter {

  import cats._, data._, implicits._
  import org.atnos.eff._
  import org.atnos.eff.all._
  import org.atnos.eff.future._
  import org.atnos.eff.syntax.all._
  import org.atnos.eff.syntax.future._

  import TeamInfo._ // get the algebra

  private def getTeamInfoFromSlack(cfg: SlackTeamInfoConfig[String],
                                   token: SlackAccessToken[String],
                                   httpService : HttpService)(implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val supervisor = actorSystem.actorOf(Props[SupervisorRestartN], s"supervisorRestartN_${java.util.UUID.randomUUID.toString}")
    implicit val createActorTimeout = Timeout(200 milliseconds)

    val actor = Await.result( (supervisor ? Props(new SlackTeamInfoActor(cfg, token, httpService))).mapTo[ActorRef], createActorTimeout.duration)
    val timeout = Timeout(cfg.timeout seconds)
    // TODO: Once Eff-Monad upgrades to allow waitFor, retryUntil, we will take
    // this abomination out.
    // Rationale for allowing the sleep to occur is because the ask i.e. ? will
    // occur before the http-request which would return a None.
    Thread.sleep(cfg.timeout * 1000)
    futureDelay[Stack, io.circe.Json](Await.result((actor ? GetTeamInfo).mapTo[io.circe.Json], timeout.duration))
  }

  private def getTeamEmojiFromSlack(cfg: SlackEmojiListConfig[String],
                                    token: SlackAccessToken[String],
                                    httpService : HttpService)(implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val supervisor = actorSystem.actorOf(Props[SupervisorRestartN], s"supervisorRestartN_${java.util.UUID.randomUUID.toString}")
    implicit val createActorTimeout = Timeout(200 milliseconds)

    val actor = Await.result( (supervisor ? Props(new SlackEmojiListActor(cfg, token, httpService))).mapTo[ActorRef], createActorTimeout.duration)
    val timeout = Timeout(cfg.timeout seconds)
    // TODO: Once Eff-Monad upgrades to allow waitFor, retryUntil, we will take
    // this abomination out.
    // Rationale for allowing the sleep to occur is because the ask i.e. ? will
    // occur before the http-request which would return a None.
    Thread.sleep(cfg.timeout * 1000)
    futureDelay[Stack, io.circe.Json](Await.result((actor ? GetTeamEmoji).mapTo[io.circe.Json], timeout.duration))
  }

  /** 
    * Obtain the team info from Slack based on the token, this process ha no
    * pagination as compared to other APIs for the simple reason that Slack's
    * API does not have one either.
    *
    * Caveat: the team's identifier (i.e. team id) is implicit as its retained
    * by slack and implied by the slack token.
    * 
    * @param cfg configuration
    * @param token slack token
    * @param httpService 
    */
  def getTeamInfo(slackTeamInfoCfg: SlackTeamInfoConfig[String],
                  slackEmojiListCfg : SlackEmojiListConfig[String],
                  httpService : HttpService)(implicit actorSystem:
                  ActorSystem, actorMat: ActorMaterializer) : Eff[Stack, Either[io.circe.DecodingFailure, Team]] = for {
    token  <- ask[Stack, SlackAccessToken[String]]
    _      <- tell[Stack, String]("[Get-TeamInfo] Slack access token retrieved.")
    team   <- getTeamInfoFromSlack(slackTeamInfoCfg, token, httpService)
    _      <- tell[Stack, String]("[Get-TeamInfo] Slack team info retrieved.")
    emojis <- getTeamEmojiFromSlack(slackEmojiListCfg, token, httpService)
    _      <- tell[Stack, String]("[Get-TeamEmoji] Slack team emojis retrieved.")
  } yield JsonCodec.extractNMerge(team)(emojis)

}
