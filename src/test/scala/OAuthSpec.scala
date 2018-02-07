package slacks.core.program

import org.specs2.ScalaCheck
import org.specs2.mutable._
import akka.http.scaladsl.model.{HttpHeader, HttpResponse, StatusCodes}
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

import providers.slack.models.SlackAccessToken
import slacks.core.models.Token

class OAuthSpec(implicit ee: ExecutionEnv) extends Specification with ScalaCheck with Specs2RouteTest { override def is = sequential ^ s2"""

  The oauth stack can be used to
    return the client secret key    $getSecretKey
    return slack's access token     $getAccessToken
    return slack's x-oauth-scopes   $getAuthScope
  """

  def getSecretKey = {
    import OAuthInterpreter._
    val result = getClientCredentials.runReader(("raymondtay", "1122ccc".some)).runEval.runWriter.run
    result._1 === "1122ccc".some
  }

  // Considering that we cannot and quite possibly a terrible idea to connect
  // to Slack everytime we needed something, we have to simulate the comings
  // and goings.
  //
  val simulatedRoute =
    Directives.get {
      path("fake.slack.com") {
        complete("fake-code-from-slack-1337") // simulates the return code from Slack
      }
    }

  def getAccessToken = {
    Get("/fake.slack.com") ~> simulatedRoute ~> check {
      import OAuthInterpreter._
      import akka.testkit._
      import scala.concurrent._, duration._

      val code = responseAs[String]

      implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
      import slacks.core.config.Config
      Config.accessConfig match { // this tests the configuration loaded in application.conf
        case Right(cfg) ⇒
          val timeout = cfg.timeout.second.dilated
          val token =
            Await.result(
              getSlackAccessToken(cfg, code, new FakeOAuthHttpService).
                runReader(("raymond", "raymond-secret-key".some)).
                runWriter.runSequential, timeout)
          token._1.get.access_token.value === "test-token"
          token._1.get.scope === List("read")
        case Left(_)  ⇒ false
      }
    }
  }

  def getAuthScope = {
      import OAuthInterpreter._
      import akka.testkit._
      import scala.concurrent._, duration._

      val token = Token("xoxp-","test-token")

      implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
      import slacks.core.config.Config
      (Config.authConfig : @unchecked) match { // this tests the configuration loaded in application.conf
        case Right(cfg) ⇒
          val timeout = cfg.scope.timeout.second.dilated
          val (slackToken, logs) =
            Await.result(
              getOAuthScope(cfg.scope, token, new FakeOAuthScopeHttpService).
                runWriter.runSequential, timeout)
          slackToken must beSome((t: SlackAccessToken[String]) ⇒ t.scope.size must be_==(2))
      }
  }
}
