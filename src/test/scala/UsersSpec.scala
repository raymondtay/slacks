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

class UsersSpec(implicit ee: ExecutionEnv) extends Specification with ScalaCheck with Specs2RouteTest { override def is = sequential ^ s2"""

  OpenTracing Disabled
  --------------------------------------------------------------
  The users stack can be used to
    return all users in the team $getUsers

  """

  val simulatedRoute = 
    Directives.get {
      path("fake.slack.com/api/users.list") {
        val json = """{}"""
        complete(json) // simulates json back from Slack
      }
    }

  def getUsers = {
      import UsersInterpreter._
      import scala.concurrent._, duration._

      implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
      import slacks.core.config.Config
      Config.usersListConfig match { // this tests the configuration loaded in application.conf
        case Right(cfg) ⇒
          val (retrievedUsers, logInfo) =
            Await.result(
              getAllUsers(cfg, new FakeGetAllUsersHttpService).
                runReader(SlackAccessToken("fake-slack-access-token", "users:list" :: Nil)).  runWriter.runSequential, 9 second)
          retrievedUsers.users.size == 20
        case Left(_)  ⇒ false
      }
  }

}
