package slacks.core.program

/**
  * This is the interpreter for the Slack Users Algebra
  * @author Raymond Tay
  * @version 1.0
  */

import scala.language.postfixOps

import providers.slack.algebra._
import slacks.core.program.supervisor.SupervisorRestartN
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
    * @param config
    * @param token
    * @param httpService
    */
  private def getAllUsersFromSlack(cfg: SlackUsersListConfig[String],
                                   token: SlackAccessToken[String],
                                   httpService : HttpService)(implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val supervisor = actorSystem.actorOf(Props[SupervisorRestartN], s"supervisorRestartN_${java.util.UUID.randomUUID.toString}")
    implicit val createActorTimeout = Timeout(3 seconds)
    val actor = Await.result((supervisor ? Props(new SlackUsersActor(cfg, token, httpService))).mapTo[ActorRef], createActorTimeout.duration)
    val timeout = Timeout(cfg.timeout seconds)
    futureDelay[GetUsersStack, UserList](
      Await.result((actor ? GetUsers).mapTo[UserList], timeout.duration)
    )
  }

}

