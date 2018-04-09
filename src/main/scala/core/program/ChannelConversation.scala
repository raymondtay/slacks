package slacks.core.program 

import scala.language.postfixOps

import providers.slack.algebra._
import providers.slack.models._
import slacks.core.program.supervisor.SupervisorRestartN
import slacks.core.config.{SlackBlacklistMessageForUserMentions, SlackChannelReadConfig}

import akka.actor._
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}

/**
  * Collects and aggregates the channel conversations on a per-channel basis
  * @author Raymond Tay
  * @version 1.0
  */

object ChannelConversationInterpreter {

  import cats._, data._, implicits._
  import org.atnos.eff._
  import org.atnos.eff.all._
  import org.atnos.eff.future._
  import org.atnos.eff.syntax.all._
  import org.atnos.eff.syntax.future._

  import Channels._

  @deprecated(message = "To be dropped in favour of 'conversation' APIs", since = "0.1-SNAPSHOT")
  private def getChannelHistoryFromSlack(channelId: ChannelId, 
                                         cfg: SlackChannelReadConfig[String], 
                                         token: SlackAccessToken[String],
                                         httpService : HttpService)(implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val supervisor = actorSystem.actorOf(Props[SupervisorRestartN], s"supervisorRestartN_${java.util.UUID.randomUUID.toString}")
    implicit val createActorTimeout = Timeout(3 seconds)
    val actor = Await.result((supervisor ? Props(new SlackChannelHistoryActor(channelId, cfg, token, httpService))).mapTo[ActorRef], createActorTimeout.duration)
    val timeout = Timeout(cfg.timeout seconds)
    futureDelay[Stack, Messages](
      Await.result((actor ? GetChannelHistory).mapTo[Messages], timeout.duration)
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
    * @param channelId
    * @param config
    * @param blacklistCfg used for data processing of white-listed message types
    * @param token
    * @param httpService
    */
  private def getChannelConversationHistoryFromSlack(channelId: ChannelId, 
                                                     cfg: SlackChannelReadConfig[String], 
                                                     blacklistCfg: SlackBlacklistMessageForUserMentions,
                                                     token: SlackAccessToken[String],
                                                     httpService : HttpService)(implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val supervisor = actorSystem.actorOf(Props[SupervisorRestartN], s"supervisorRestartN_${java.util.UUID.randomUUID.toString}")
    implicit val createActorTimeout = Timeout(3 seconds)
    val actor = Await.result((supervisor ? Props(new SlackConversationHistoryActor(channelId, cfg, blacklistCfg, token, httpService))).mapTo[ActorRef], createActorTimeout.duration)
    val timeout = Timeout(cfg.timeout seconds)
    futureDelay[Stack, SievedMessages](
      Await.result((actor ? GetConversationHistory).mapTo[SievedMessages], timeout.duration)
    )
  }
 
   /** 
    * Obtain the channel's conversation history(from all time) from Slack based on the token,
    * the process will handle the pagination mechanism embedded in Slack.
    * 
    * We leverage the conversation APIs in Slack 
    * @param cfg configuration
    * @param blacklistCfg used for data processing of white-listed message types
    * @param token slack token
    * @param httpService 
    */
  def getChannelConversationHistory(channelId: ChannelId,
                                    cfg: SlackChannelReadConfig[String], 
                                    blacklistCfg: SlackBlacklistMessageForUserMentions,
                                    httpService : HttpService)(implicit actorSystem:
                                    ActorSystem, actorMat: ActorMaterializer) : Eff[Stack, SievedMessages] = for {
    token    <- ask[Stack, SlackAccessToken[String]]
    _        <- tell[Stack, String]("[Get-Channel-Conversation-History] Slack access token retrieved.")
    messages <- getChannelConversationHistoryFromSlack(channelId, cfg, blacklistCfg, token, httpService)   
    _        <- tell[Stack, String]("[Get-Channel-Conversation-History] Slack channel(s) info retrieved.")
  } yield messages

  /** 
    * Obtain the channel's history(from all time) from Slack based on the token,
    * the process will handle the pagination mechanism embedded in Slack.
    *
    * We leverage the channel APIs in Slack
    * @param cfg configuration
    * @param token slack token
    * @param httpService 
    */
  @deprecated(message = "To be dropped in favour of 'conversation' APIs", since = "0.1-SNAPSHOT")
  def getChannelHistory(channelId: ChannelId,
                        cfg: SlackChannelReadConfig[String], 
                        httpService : HttpService)(implicit actorSystem:
                        ActorSystem, actorMat: ActorMaterializer) : Eff[Stack, Messages] = for {
    token    <- ask[Stack, SlackAccessToken[String]]
    _        <- tell[Stack, String]("[Get-Channel-History] Slack access token retrieved.")
    messages <- getChannelHistoryFromSlack(channelId, cfg, token, httpService)   
    _        <- tell[Stack, String]("[Get-Channel-History] Slack channel(s) info retrieved.")
  } yield messages

  import slacks.core.tracer.TracerFunctions._
  type ChannelHistoryStack = Fx.fx5[ReadTracer, LogTracer, Throwable Either ?, WriterString, Eval]
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext

  /** 
    * Obtain the channel's history (from all time) from Slack based on the token,
    * the process will handle the pagination mechanism embedded in Slack.
    * This action is traced via OpenTracing. See http://opentracing.io
    *
    * @param cfg configuration
    * @param token slack token
    * @param message a map of (k,v) pairs to be passed for tracing 
    * @param httpService
    */
  def traceGetChannelHistories(cfg: SlackChannelReadConfig[String],
                               channelId: String,
                               httpService : HttpService,
                               message : slacks.core.tracer.Message, 
                               token : SlackAccessToken[String])
                               (implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) : Eff[ChannelHistoryStack, scala.concurrent.Future[(Messages, List[String])]] =
  for {
    tracer  <- ask[ChannelHistoryStack, io.opentracing.Tracer].into[ChannelHistoryStack]
    _       <- tell[ChannelHistoryStack,String](s"Got the tracer from the context.").into[ChannelHistoryStack]
    result  <- traceEffect(getChannelHistory(channelId, cfg, httpService).runReader(token).runWriter.runSequential)(message).into[ChannelHistoryStack]
  } yield result

  /** 
    * Obtain the channel's conversations histories (from all time) from Slack based on the token,
    * the process will handle the pagination mechanism embedded in Slack.
    * This action is traced via OpenTracing. See http://opentracing.io
    *
    * @param cfg configuration
    * @param blacklistCfg used for data processing of white-listed message types
    * @param token slack token
    * @param message a map of (k,v) pairs to be passed for tracing 
    * @param httpService
    */
  def traceGetChannelConversationHistories(cfg: SlackChannelReadConfig[String],
                               blacklistCfg: SlackBlacklistMessageForUserMentions,
                               channelId: String,
                               httpService : HttpService,
                               message : slacks.core.tracer.Message, 
                               token : SlackAccessToken[String])
                               (implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) : Eff[ChannelHistoryStack, scala.concurrent.Future[(SievedMessages, List[String])]] =
  for {
    tracer  <- ask[ChannelHistoryStack, io.opentracing.Tracer].into[ChannelHistoryStack]
    _       <- tell[ChannelHistoryStack,String](s"Got the tracer from the context.").into[ChannelHistoryStack]
    result  <- traceEffect(getChannelConversationHistory(channelId, cfg, blacklistCfg, httpService).runReader(token).runWriter.runSequential)(message).into[ChannelHistoryStack]
  } yield result

}


