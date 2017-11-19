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

class ChannelSpec(implicit ee: ExecutionEnv) extends Specification with ScalaCheck with Specs2RouteTest { override def is = s2"""

  The channel stack can be used to
    return the channels contained in the workspace     $getChannelInfo
  """

  val simulatedRoute = 
    Directives.get {
      path("fake.slack.com/api/channels.list") {
        val json = """
        {"ok":true,"channels":[{"id":"C024Z5MQT","name":"general","is_channel":true,"created":1391648421,"creator":"U024Z5MQP","is_archived":false,"is_general":true,"unlinked":0,"name_normalized":"general","is_shared":false,"is_org_shared":false,"is_member":true,"is_private":false,"is_mpim":false,"members":["U024Z5MQP","U024ZCABY","U024ZCR04","U024ZH7HL","U0250SQLD","U02518S6S","U029A9L6M","U029ACXNZ","U02EJ9QKJ","U02MR8EG8","U02PY6S73","U030MHXHX","U034URXDR","U03C98L5C","U03CKFGU5","U047EAUB4","U0483ASQP","U049K6V1G","U04MGHVRY","U0790EWUW","U086LTM6W","U08GD90CC","U08TDQVNG","U0AM39YTX","U0CDW37RA","U0CE9A2E5","U0DATFFH9","U0F3F6F38","U0FB8THB8","U0FJKS5MM","U0G1H4L3E","U0GAZLRPW","U0L251X5W","U0LPSJQR0","U0PL0HUHG","U0RBSN9D1","U0X3L1PS7","U10H6PUSJ","U17RGMDU4","U193XDML7","U1NG7CPBK","U1NGC3ZPT","U1SF636UB","U23D7H5MZ","U2748C06S","U2FQG2G9F","U2M8UH9SM","U2Q2U37SA","U2YAZS40Y","U2Z0ARK2P","U31B3PV17","U37BF9457","U39R1AT9D","U3ACT6Z2P","U3LRTQ8G1","U3NND6PV1","U3RUCKH5J","U41CSF56Z","U43LNT57T","U43Q2RJ8H","U497EFER0","U4AFYEWBG","U4B93DBDX","U4BUQR94L","U4U2WKX7X","U4W385673","U543VFD3Q","U56JZMQ0Y","U575BN3H9","U577BHBNW","U58LY38Q6","U5K7JUATE","U5TEUA60Z","U5UG5NU6T","U5ZV5797E","U642GGK9R","U664CEM4L","U66T9CNBG","U6QFZ585N","U6R7SU9P0","U74K31TA9","U7JKEFHM0","U7SG2QG2D","U7V7V7NFM","U81GPG5HV"],"topic":{"value":"the day @thu was dethroned https:\/\/nugit.slack.com\/archives\/general\/p1476945193000075","creator":"U04MGHVRY","last_set":1499417480},"purpose":{"value":"The #general channel is for team-wide communication and announcements. All team members are in this channel.","creator":"","last_set":0},"previous_names":[],"num_members":38},{"id":"C024Z65M7","name":"dev-log","is_channel":true,"created":1391651270,"creator":"U024Z5MQP","is_archived":true,"is_general":false,"unlinked":0,"name_normalized":"dev-log","is_shared":false,"is_org_shared":false,"is_member":false,"is_private":false,"is_mpim":false,"members":[],"topic":{"value":"Updates on Github commits across all Nugit repositories.","creator":"U024Z5MQP","last_set":1400065716},"purpose":{"value":"Updates on Github commits across all Nugit repositories","creator":"U024Z5MQP","last_set":1400065746},"previous_names":["dev"],"num_members":0}],"response_metadata":{"next_cursor":"dGVhbTpDMDI0WkNWOFg="}}
        """
        complete(json) // simulates json back from Slack
      } ~ 
      path("fake.slack.com") {
        complete("ok")
      }
    }

  def getChannelInfo = {
    Get("/fake.slack.com") ~> simulatedRoute ~> check {
      import ChannelsInterpreter._
      import scala.concurrent._, duration._
      import scala.concurrent.ExecutionContext.Implicits.global
  
      val code = responseAs[String]

      implicit val scheduler = ExecutorServices.schedulerFromScheduledExecutorService(ee.ses)
      import slacks.core.config.Config
      Config.channelConfig match { // this tests the configuration loaded in application.conf
        case Right(cfg) ⇒ 
          val (channels, logInfo) =
            Await.result(
              getChannelList(cfg, new FakeChannelHttpService).
                runReader(SlackAccessToken("fake-slack-token",
                  "channels.list" :: Nil)).
                runWriter.runSequential, 9 second)
          channels.xs.size != 0
        case Left(_)  ⇒ false
      }
    }
  }
}
