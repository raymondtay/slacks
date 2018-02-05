package slacks.core.program

import org.specs2.ScalaCheck
import org.specs2.mutable._
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
import providers.slack.algebra.TeamId
import providers.slack.models._

class TeamInfoSpec(implicit ee: ExecutionEnv) extends Specification with Specs2RouteTest { override def is = sequential ^ s2"""

  OpenTracing Disabled
  --------------------------------------------------------------
  The union of team.info and emoji.list jsons should produce a valid 'Team' model when both remote data retrievals are OK... $verifyMergeOfJsonsWhenOK
  The union of team.info and emoji.list jsons should produce a valid 'Team' model when both remote data retrievals are NOT OK... $verifyMergeOfJsonsWhenNOK
  """

  def verifyMergeOfJsonsWhenOK = {
    import JsonCodec.extractNMerge 
    import scala.concurrent._, duration._

    import TeamInfoInterpreter._

    implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
    import slacks.core.config.Config
    ((Config.emojiListConfig, Config.teamInfoConfig) : @unchecked) match { // this tests the configuration loaded in application.conf
      case (Right(emojiListCfg),Right(teamInfoCfg)) ⇒
        val (teamModel, logInfo) =
          Await.result(
            getTeamInfo(teamInfoCfg, emojiListCfg, new FakeTeamInfoHttpService).
              runReader(SlackAccessToken("fake-slack-access-token", "users:list" :: Nil)).runWriter.runSequential, 5 second)

        teamModel._1 must not be empty
        teamModel._2 must beRight((e: Team) ⇒ e.name.size must not be_==(0))
        teamModel._2 must beRight((e: Team) ⇒ e.domain.size must not be_==(0))
        teamModel._2 must beRight((e: Team) ⇒ e.email_domain.size must not be_==(0))
        teamModel._2 must beRight((e: Team) ⇒ e.image_132.size must not be_==(0))
        teamModel._2 must beRight((e: Team) ⇒ e.emojis.size must not be_==(0))
    }
  }

  def verifyMergeOfJsonsWhenNOK = {
    import JsonCodec.extractNMerge 
    import scala.concurrent._, duration._

    import TeamInfoInterpreter._

    implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
    import slacks.core.config.Config
    ((Config.emojiListConfig, Config.teamInfoConfig) : @unchecked) match { // this tests the configuration loaded in application.conf
      case (Right(emojiListCfg),Right(teamInfoCfg)) ⇒
        val (teamModel, logInfo) =
          Await.result(
            getTeamInfo(teamInfoCfg, emojiListCfg, new FakeTeamInfoHttpServiceReturnsInvalidJson).
              runReader(SlackAccessToken("fake-slack-access-token", "users:list" :: Nil)).runWriter.runSequential, 5 second)

        teamModel._1 must not be empty
        teamModel._2 must beLeft
    }
  }

}
