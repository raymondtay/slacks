package slacks.core.program 

import providers.slack.algebra._
import providers.slack.models._
import slacks.core.config.SlackChannelReadConfig

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

  private def getChannelHistoryFromSlack(channelId: ChannelId, 
                                         cfg: SlackChannelReadConfig[String], 
                                         token: SlackAccessToken[String],
                                         httpService : HttpService)(implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val actor = actorSystem.actorOf(Props(new SlackChannelHistoryActor(channelId, cfg, token, httpService)))
    implicit val timeout = Timeout(cfg.timeout seconds)
    // TODO: Once Eff-Monad upgrades to allow waitFor, retryUntil, we will take
    // this abomination out.
    // Rationale for allowing the sleep to occur is because the ask i.e. ? will
    // occur before the http-request which would return a None.
    Thread.sleep(cfg.timeout * 1000)
    futureDelay[Stack, Messages](Await.result((actor ? GetChannelHistory).mapTo[Messages], timeout.duration))
  }
 

  /** 
    * Obtain the channel's history(from all time) from Slack based on the token,
    * the process will handle the pagination mechanism embedded in Slack.
    *
    * @param cfg configuration
    * @param token slack token
    * @param httpService 
    */
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
}


