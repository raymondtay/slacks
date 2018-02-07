package slacks.core.program

/**
  * This is the interpreter for the Slack Channel Algebra
  * @author Raymond Tay
  * @version 1.0
  */

import scala.language.postfixOps

import providers.slack.algebra._
import slacks.core.program.supervisor.SupervisorRestartN
import slacks.core.config.SlackChannelListConfig

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

  private def getChannelsFromSlack(cfg: SlackChannelListConfig[String], 
                                   token: SlackAccessToken[String],
                                   httpService : HttpService)(implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val supervisor = actorSystem.actorOf(Props[SupervisorRestartN], s"supervisorRestartN_${java.util.UUID.randomUUID.toString}")
    implicit val createActorTimeout = Timeout(1 seconds)

    val actor = Await.result( (supervisor ? Props(new SlackChannelActor(cfg, token, httpService))).mapTo[ActorRef], createActorTimeout.duration)
    val timeout = Timeout(cfg.timeout seconds)
    // TODO: Once Eff-Monad upgrades to allow waitFor, retryUntil, we will take
    // this abomination out.
    // Rationale for allowing the sleep to occur is because the ask i.e. ? will
    // occur before the http-request which would return a None.
    Thread.sleep(cfg.timeout * 1000)
    futureDelay[Stack, Storage](Await.result((actor ? GetChannelListing).mapTo[Storage], timeout.duration))
  }
 
  /** 
    * Obtain the channel listing from Slack based on the token,
    * the process will handle the pagination mechanism embedded in Slack.
    *
    * @param cfg configuration
    * @param token slack token
    * @param httpService 
    */
  def getChannelList(cfg: SlackChannelListConfig[String], 
                     httpService : HttpService)(implicit actorSystem:
                     ActorSystem, actorMat: ActorMaterializer) : Eff[Stack, Storage] = for {
    token    <- ask[Stack, SlackAccessToken[String]]
    _        <- tell[Stack, String]("[Get-Channel-List] Slack access token retrieved.")
    channels <- getChannelsFromSlack(cfg, token, httpService)   
    _        <- tell[Stack, String]("[Get-Channel-List] Slack channel(s) info retrieved.")
  } yield channels

  import slacks.core.tracer.TracerFunctions._
  type ChannelStack = Fx.fx5[ReadTracer, LogTracer, Throwable Either ?, WriterString, Eval]
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext

  /** 
    * Obtain the channel listing from Slack based on the token,
    * the process will handle the pagination mechanism embedded in Slack.
    * This action is traced via OpenTracing. See http://opentracing.io
    *
    * @param cfg configuration
    * @param token slack token
    * @param message a map of (k,v) pairs to be passed for tracing 
    * @param httpService
    */
  def traceGetChannelList(cfg: SlackChannelListConfig[String],
                          httpService : HttpService,
                          message : slacks.core.tracer.Message, 
                          token : SlackAccessToken[String])
                         (implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) : Eff[ChannelStack, scala.concurrent.Future[(Storage, List[String])]] =
  for {
    tracer  <- ask[ChannelStack, io.opentracing.Tracer].into[ChannelStack]
    _       <- tell[ChannelStack,String](s"Got the tracer from the context.").into[ChannelStack]
    result  <- traceEffect(getChannelList(cfg, httpService).runReader(token).runWriter.runSequential)(message).into[ChannelStack]
  } yield result
}
