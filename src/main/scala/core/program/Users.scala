package slacks.core.program

/**
  * This is the interpreter for the Slack Users Algebra
  * @author Raymond Tay
  * @version 1.0
  */

import providers.slack.algebra._
import slacks.core.config.SlackUsersListConfig

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}
import providers.slack.models._

object UsersInterpreter {
  import cats._, data._, implicits._
  import org.atnos.eff._
  import org.atnos.eff.all._
  import org.atnos.eff.future._
  import org.atnos.eff.syntax.all._
  import org.atnos.eff.syntax.future._

  import providers.slack.algebra.Users._

  /**
    * Extracts all users from Slack
    * @param cfg configuration
    * @param httpService
    * @return list of user objects
    */
  def getAllUsers(cfg: SlackUsersListConfig[String],
                  httpService : HttpService)(implicit actorSystem:
                  ActorSystem, actorMat: ActorMaterializer) : Eff[GetUsersStack, UserList] = for {
    token <- ask[GetUsersStack, SlackAccessToken[String]]
    _     <- tell[GetUsersStack, String]("[Get-All-Users] Slack access token retrieved.")
    users <- getAllUsersFromSlack(cfg, token, httpService)
    _     <- tell[GetUsersStack, String]("[Get-All-Users] Slack users information retrieved.")
  } yield users

  private def getAllUsersFromSlack(cfg: SlackUsersListConfig[String],
                                   token: SlackAccessToken[String],
                                   httpService : HttpService)(implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val actor = actorSystem.actorOf(Props(new SlackUsersActor(cfg, token, httpService)))
    implicit val timeout = Timeout(cfg.timeout seconds)
    Thread.sleep(cfg.timeout * 1000)
    futureDelay[GetUsersStack, UserList](Await.result((actor ? GetUsers).mapTo[UserList], timeout.duration))
  }

}

