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

  /**
    * The async processing (via Actors) will continuously pull data from Slack
    * and store into its state while we retrieve it asynchronously till we
    * exhausted all our timeouts.
    *
    * How we have chose to do this is roughly as follows:
    * Grab the processed data from the worker actor and check if the size
    * is GT 0; if yes, we discontinue if no we wait 2 seconds longer than the
    * previous wait-time (till we reach the maximum timeout). This strategy
    * allows the async process to work through the data wrangling.
    *
    * @param config
    * @param token
    * @param httpService
    */
  private def getTeamInfoFromSlack(cfg: SlackTeamInfoConfig[String],
                                   token: SlackAccessToken[String],
                                   httpService : HttpService)(implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val supervisor = actorSystem.actorOf(Props[SupervisorRestartN], s"supervisorRestartN_${java.util.UUID.randomUUID.toString}")
    implicit val createActorTimeout = Timeout(3 seconds)

    val actor = Await.result( (supervisor ? Props(new SlackTeamInfoActor(cfg, token, httpService))).mapTo[ActorRef], createActorTimeout.duration)
    val timeout = Timeout(cfg.timeout seconds)
    futureDelay[Stack, (TeamId, io.circe.Json)](
      Await.result((actor ? GetTeamInfo).mapTo[(TeamId, io.circe.Json)], timeout.duration)
    )
  }

  /**
    * The async processing (via Actors) will continuously pull data from Slack
    * and store into its state while we retrieve it asynchronously till we
    * exhausted all our timeouts.
    *
    * How we have chose to do this is roughly as follows:
    * Grab the processed data from the worker actor and check if the size
    * is GT 0; if yes, we discontinue if no we wait 2 seconds longer than the
    * previous wait-time (till we reach the maximum timeout). This strategy
    * allows the async process to work through the data wrangling.
    *
    * @param config
    * @param token
    * @param httpService
    */
 private def getTeamEmojiFromSlack(cfg: SlackEmojiListConfig[String],
                                    token: SlackAccessToken[String],
                                    httpService : HttpService)(implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val supervisor = actorSystem.actorOf(Props[SupervisorRestartN], s"supervisorRestartN_${java.util.UUID.randomUUID.toString}")
    implicit val createActorTimeout = Timeout(3 seconds)

    val actor = Await.result( (supervisor ? Props(new SlackEmojiListActor(cfg, token, httpService))).mapTo[ActorRef], createActorTimeout.duration)
    val timeout = Timeout(cfg.timeout seconds)
    futureDelay[Stack, io.circe.Json](
      Await.result((actor ? GetTeamEmoji).mapTo[io.circe.Json], timeout.duration)
    )
  }

  /**
    * Obtain the team info from Slack based on the token, this process ha no
    * pagination as compared to other APIs for the simple reason that Slack's
    * API does not have one either.
    *
    * Caveat: the team's identifier (i.e. team id) is implicit as its retained
    * by slack and implied by the slack token.
    *
    * @param teamInfocfg configuration to locate slack's team api
    * @param emojiListcfg configuration to locate to slack's emoji api
    * @param token slack token
    * @param httpService
    */
  def getTeamInfo(slackTeamInfoCfg: SlackTeamInfoConfig[String],
                  slackEmojiListCfg : SlackEmojiListConfig[String],
                  httpService : HttpService)(implicit actorSystem:
                  ActorSystem, actorMat: ActorMaterializer) : Eff[Stack, (TeamId, Either[io.circe.DecodingFailure, Team])] = for {
    token  <- ask[Stack, SlackAccessToken[String]]
    _      <- tell[Stack, String]("[Get-TeamInfo] Slack access token retrieved.")
    team   <- getTeamInfoFromSlack(slackTeamInfoCfg, token, httpService)
    _      <- tell[Stack, String]("[Get-TeamInfo] Slack team info retrieved.")
    emojis <- getTeamEmojiFromSlack(slackEmojiListCfg, token, httpService)
    _      <- tell[Stack, String]("[Get-TeamEmoji] Slack team emojis retrieved.")
  } yield (team._1, JsonCodec.extractNMerge(team._2)(emojis))

  /**
    * Obtain the team's id from Slack based on the token, this process ha no
    * pagination as compared to other APIs for the simple reason that Slack's
    * API does not have one either.
    *
    * Caveat: the team's identifier (i.e. team id) is implicit as its retained
    * by slack and implied by the slack token.
    *
    * @param teamInfocfg configuration to locate slack's team api
    * @param token slack token
    * @param httpService
    */
  def getTeam(slackTeamInfoCfg: SlackTeamInfoConfig[String],
              httpService : HttpService)(implicit actorSystem:
              ActorSystem, actorMat: ActorMaterializer) : Eff[Stack, TeamId] = for {
    token  <- ask[Stack, SlackAccessToken[String]]
    _      <- tell[Stack, String]("[Get-Team] Slack access token retrieved.")
    team   <- getTeamInfoFromSlack(slackTeamInfoCfg, token, httpService)
    _      <- tell[Stack, String]("[Get-Team] Slack team retrieved.")
  } yield team._1

}
