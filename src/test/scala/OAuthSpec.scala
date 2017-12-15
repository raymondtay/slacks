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

class OAuthSpec(implicit ee: ExecutionEnv) extends Specification with ScalaCheck with Specs2RouteTest { override def is = s2"""

  The oauth stack can be used to
    return the client secret key    $getSecretKey
    return slack's access token     $getAccessToken
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
      path("fake.slack.com/api/oauth.access") { // this must match application.conf
        parameters('client_id, 'client_secret, 'code) {
          (cId, cS, code) ⇒
            val json = """
            {
              "access_token": "xoxp-23984754863-2348975623103",
              "scope": "read"
            }
            """
            complete(json)
        }
      } ~ 
      path("fake.slack.com") {
        complete("fake-code-from-slack-1337") // simulates the return code from Slack
      }
    }

  def getAccessToken = {
    Get("/fake.slack.com") ~> simulatedRoute ~> check {
      import OAuthInterpreter._
      import akka.testkit._
      import scala.concurrent._, duration._
 
      val timeout = 2.second.dilated
      val code = responseAs[String]

      println(s"code: => $code")
      implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
      import slacks.core.config.Config
      Config.accessConfig match { // this tests the configuration loaded in application.conf
        case Right(cfg) ⇒ 
          val token =
            Await.result(
              getSlackAccessToken(cfg, code, new FakeOAuthHttpService).
                runReader(("raymond", "raymond-secret-key".some)).
                runWriter.runSequential, timeout)
          println(s"token: => $token")
          token._1.get.access_token === "test-token"
          token._1.get.scope === List("read")
        case Left(_)  ⇒ false
      }
    }
  }
}
