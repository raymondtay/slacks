package slacks.core.program

import org.specs2.ScalaCheck
import org.specs2.mutable._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.Specs2RouteTest
import akka.http.scaladsl.server._
import Directives._
import org.specs2.concurrent.ExecutionEnv
import org.scalacheck._
import com.typesafe.config._
import Arbitrary._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}
import org.atnos.eff._
import org.atnos.eff.future._
import org.atnos.eff.all._
import org.atnos.eff.syntax.all._
import org.atnos.eff.syntax.future._
import cats._, implicits._
import akka.actor._
import akka.stream._
import providers.slack.models._
import slacks.core.models.Token

class ChannelSpec(implicit ee: ExecutionEnv) extends Specification with ScalaCheck with Specs2RouteTest { override def is = sequential ^ s2"""
  OpenTracing Disabled
  --------------------------------------------------------------
  The channel stack can be used to
    return the channels contained in the workspace                             $getChannelListing
    return the detailed information on an individual channel                   $getChannelHistory
    return "empty" information on an individual channel                        $getChannelHistoryWhenRemoteIsDown
    return bot and user messages carrying attachments on an individual channel $getChannelConversationHistory
    return "empty" bot and user messages an individual channel when remote is down $getChannelConversationHistoryWhenRemoteIsDown

  OpenTracing Enabled
  --------------------------------------------------------------
  The channel stack can be used to
    return the channels contained in the workspace           $getChannelListingTraced
    return the detailed information on an individual channel $getChannelHistoryTraced
    return bot and user messages carrying attachments on an individual channel $getChannelConversationHistoryTraced
  """

  def getChannelListing = {
    import ChannelsInterpreter._
    import scala.concurrent._, duration._

    implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
    import slacks.core.config.Config
    (Config.channelListConfig : @unchecked) match { // this tests the configuration loaded in application.conf
      case Right(cfg) ⇒
        val (channels, logInfo) =
          Await.result(
            getChannelList(cfg, new FakeChannelListingHttpService).
              runReader(SlackAccessToken(Token("xoxp-","fake-slack-token"), "channels:list" :: Nil)).
              runWriter.
              runSequential, cfg.timeout second)
        channels.xs.size must be_>(0)
    }
  }

  def getChannelHistory = {
    import ChannelConversationInterpreter._
    import scala.concurrent._, duration._

    val channelId = "C024Z5MQT"

    implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
    import slacks.core.config.Config
    (Config.channelReadConfig : @unchecked) match { // this tests the configuration loaded in application.conf
      case Right(cfg) ⇒
        val (channels, logInfo) =
          Await.result(
            ChannelConversationInterpreter.getChannelHistory(channelId, cfg, new FakeChannelHistoryHttpService).
              runReader(SlackAccessToken(Token("xoxp-","fake-slack-token"), "channels:list" :: Nil)).
              runWriter.
              runSequential, cfg.timeout second)
        channels.xs.size must be_>(0)
    }
  }

  def getChannelHistoryWhenRemoteIsDown = {
    import ChannelConversationInterpreter._
    import scala.concurrent._, duration._

    val channelId = "C024Z5MQT"

    implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
    import slacks.core.config.Config
    (Config.channelReadConfig : @unchecked)match { // this tests the configuration loaded in application.conf
      case Right(cfg) ⇒
        val (channels, logInfo) =
          Await.result(
            ChannelConversationInterpreter.getChannelHistory(channelId, cfg, new FakeChannelHistoryHttpServiceWhenRemoteIsDown).
              runReader(SlackAccessToken(Token("xoxp-","fake-slack-token"), "channels:list" :: Nil)).
              runWriter.
              runSequential, cfg.timeout second)
        channels.xs.size must be_==(0)
    }
  }

  def getChannelConversationHistory = {
    import ChannelConversationInterpreter._
    import scala.concurrent._, duration._

    val channelId = "C024Z5MQT" // id of the `general` channel

    implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
    import slacks.core.config.Config
    ((Config.channelReadConfig : @unchecked, Config.usermentionBlacklistConfig : @unchecked) : @unchecked) match { // this tests the configuration loaded in application.conf
      case (Right(cfg), Right(blacklistedCfg)) ⇒
        val (messages, logInfo) =
          Await.result(
            ChannelConversationInterpreter.getChannelConversationHistory(channelId, cfg, blacklistedCfg, new FakeChannelConversationHistoryHttpService).
              runReader(SlackAccessToken(Token("xoxp-","fake-slack-access-token"), "channels:history" :: Nil)).
              runWriter.
              runSequential, cfg.timeout second)
        messages.botMessages.size must beBetween(0,20) /* async processing here as the data's upperbound is 20, but at least 0. */
        messages.userAttachmentMessages.size must be_==(2)
        messages.userFileShareMessages.foldLeft(0)((acc,e) ⇒ e.comments.size + acc) must be_==(5)
    }
  }

  def getChannelConversationHistoryWhenRemoteIsDown = {
    import ChannelConversationInterpreter._
    import scala.concurrent._, duration._

    val channelId = "C024Z5MQT" // id of the `general` channel

    implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
    import slacks.core.config.Config
    ((Config.channelReadConfig : @unchecked, Config.usermentionBlacklistConfig : @unchecked) : @unchecked) match { // this tests the configuration loaded in application.conf
      case (Right(cfg), Right(blacklistedCfg)) ⇒
        val (messages, logInfo) =
          Await.result(
            ChannelConversationInterpreter.getChannelConversationHistory(channelId, cfg, blacklistedCfg, new FakeChannelConversationHistoryHttpServiceWhenRemoteIsDown).
              runReader(SlackAccessToken(Token("xoxp-","fake-slack-access-token"), "channels:history" :: Nil)).
              runWriter.
              runSequential, cfg.timeout second)
        messages.botMessages.size must be_==(0)
        messages.userAttachmentMessages.size must be_==(0)
        messages.userFileShareMessages.foldLeft(0)((acc,e) ⇒ e.comments.size + acc) must be_==(0)
    }
  }

  def getChannelConversationHistoryTraced = {
    import ChannelConversationInterpreter._
    import io.opentracing.util.GlobalTracer
    import scala.concurrent._, duration._

    val channelId = "C024Z5MQT"

    // empty message-map for the trace
    val message : slacks.core.tracer.Message = collection.immutable.HashMap.empty[String,String]
    val tracer : io.opentracing.Tracer = GlobalTracer.get()

    implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
    import slacks.core.config.Config
    ((Config.channelReadConfig : @unchecked, Config.usermentionBlacklistConfig : @unchecked) : @unchecked) match { // this tests the configuration loaded in application.conf
      case (Right(cfg), Right(blacklistedCfg)) ⇒
        val (messageProcess, logs) =
            traceGetChannelConversationHistories(cfg,
                                                 blacklistedCfg,
                                                 channelId,
                                                 new FakeChannelConversationHistoryHttpService,
                                                 message,
                                                 SlackAccessToken(Token("xoxp-","fake-slack-access-token"), "channels:history" :: Nil)).runReader(tracer).runWriter.runEither.runWriter.runEval.run
        messageProcess match {
          case e @ Left(_)  ⇒ e must beLeft // should not happen
          case Right(datum) ⇒
            val (actualMessagesFuture, tracerLogs) = datum
            val (messages, botLogs) = Await.result(actualMessagesFuture, cfg.timeout seconds)
            messages.botMessages.size must beBetween(0,20) /* async processing of data here with an upperbound of 20 */
            messages.userAttachmentMessages.size must be_==(2)
        }
    }
  }

  def getChannelListingTraced = {
    import ChannelsInterpreter._
    import io.opentracing.util.GlobalTracer
    import scala.concurrent._, duration._

    // empty message-map for the trace
    val message : slacks.core.tracer.Message = collection.immutable.HashMap.empty[String,String]
    val tracer : io.opentracing.Tracer = GlobalTracer.get()

    implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
    import slacks.core.config.Config
    (Config.channelListConfig : @unchecked) match { // this tests the configuration loaded in application.conf
      case Right(cfg) ⇒
        val (channelResult, logs)  =
            traceGetChannelList(cfg,
                                new FakeChannelListingHttpService,
                                message,
                                SlackAccessToken(Token("xoxp-","fake-slack-token"), "channels.list" :: Nil)
                              ).runReader(tracer).runWriter.runEither.runWriter.runEval.run
        channelResult match {
          case e @ Left(_) ⇒ e must beLeft // should not happen
          case Right(datum) ⇒
            val (actualChannelResultsFuture, tracerLogs) = datum
            val (channels, channelLogs) = Await.result(actualChannelResultsFuture, cfg.timeout seconds)
            channels.xs.size must be_>(0)
        }
    }
  }

  def getChannelHistoryTraced = {
    import ChannelConversationInterpreter._
    import io.opentracing.util.GlobalTracer
    import scala.concurrent._, duration._

    // empty message-map for the trace
    val message : slacks.core.tracer.Message = collection.immutable.HashMap.empty[String,String]
    val tracer : io.opentracing.Tracer = GlobalTracer.get()
    val channelId = "fake-channel-id"

    implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
    import slacks.core.config.Config
    (Config.channelReadConfig : @unchecked) match { // this tests the configuration loaded in application.conf
      case Right(cfg) ⇒
        val (channelResult, logs)  =
            traceGetChannelHistories(cfg,
                                     channelId,
                                     new FakeChannelHistoryHttpService,
                                     message,
                                     SlackAccessToken(Token("xoxp-","fake-slack-token"), "channels.list" :: Nil)
                                   ).runReader(tracer).runWriter.runEither.runWriter.runEval.run
        channelResult match {
          case e @ Left(_) ⇒ e must beLeft // should not happen
          case Right(datum) ⇒
            val (actualChannelResultsFuture, tracerLogs) = datum
            val (channels, channelLogs) = Await.result(actualChannelResultsFuture, cfg.timeout seconds)
            channels.xs.size must be_>(0)
        }
    }
  }

}
