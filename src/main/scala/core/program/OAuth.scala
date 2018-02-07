package slacks.core.program

/**
  * This is the interpreter for the Slack Algebra
  * @author Raymond Tay
  * @version 1.0
  */

import scala.language.postfixOps

import slacks.core.models.Token
import providers.slack.algebra._
import slacks.core.program.supervisor.SupervisorRestartN
import slacks.core.config.{SlackAccessConfig, SlackAuthScopeConfig}

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}
import providers.slack.models._

object OAuthInterpreter {
  import cats._, data._, implicits._
  import org.atnos.eff._
  import org.atnos.eff.all._
  import org.atnos.eff.future._
  import org.atnos.eff.syntax.all._
  import org.atnos.eff.syntax.future._

  import OAuth._

  private def askFromSlack(cfg : SlackAccessConfig[String],
                           code : SlackCode,
                           credential : SlackCredentials,
                           httpService : HttpService)
                          (implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val supervisor = actorSystem.actorOf(Props[SupervisorRestartN], s"supervisorRestartN_${java.util.UUID.randomUUID.toString}")
    implicit val createActorTimeout = Timeout(1 seconds)
    val actor = Await.result((supervisor ? Props(new SlackActor(cfg, code, credential, httpService))).mapTo[ActorRef], createActorTimeout.duration)
    val timeout = Timeout(cfg.timeout seconds)
    // TODO: Once Eff-Monad upgrades to allow waitFor, retryUntil, we will take
    // this abomination out.
    // Rationale for allowing the sleep to occur is because the ask i.e. ? will
    // occur before the http-request which would return a None.
    Thread.sleep(cfg.timeout * 1000)
    futureDelay[Stack, Option[SlackAccessToken[String]]](Await.result((actor ? GetToken).mapTo[Option[SlackAccessToken[String]]], timeout.duration))
  }

  def getClientCredentials : Eff[CredentialsStack, Option[ClientSecretKey]] = for {
    datum  <- ask[CredentialsStack, (ClientId,Option[ClientSecretKey])]
    _      <- tell[CredentialsStack,String](s"Credentials is retrieved.")
  } yield datum._2

  /**
    * Attempts to get the access token from Slack via a RESTful call.
    * @param cfg the configuration object
    * @param code the slack code (10-min expiration lease)
    * @param httpService the http service
    * @return either `None` or `Some[SlackAccessToken]`
    */
  def getSlackAccessToken(cfg: SlackAccessConfig[String],
                          code : SlackCode,
                          httpService : HttpService)(implicit actorSystem: ActorSystem, actorMat: ActorMaterializer) : Eff[Stack, Option[SlackAccessToken[String]]] = for {
    credentials  <- ask[Stack, (ClientId,Option[ClientSecretKey])]
    _            <- tell[Stack,String](s"Credentials is retrieved, locally.")
    tokenF       <- askFromSlack(cfg, code, credentials, httpService)
    _            <- tell[Stack,String](s"Slack access token is retrieved.")
  } yield tokenF

  private def getScope(cfg : SlackAuthScopeConfig[String],
                       token : Token,
                       httpService : HttpService)
                       (implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val supervisor = actorSystem.actorOf(Props[SupervisorRestartN], s"supervisorRestartN_${java.util.UUID.randomUUID.toString}")
    implicit val createActorTimeout = Timeout(1 seconds)
    val actor = Await.result((supervisor ? Props(new SlackScopeActor(cfg, token, httpService))).mapTo[ActorRef], createActorTimeout.duration)
    val timeout = Timeout(cfg.timeout seconds)
    // TODO: Once Eff-Monad upgrades to allow waitFor, retryUntil, we will take
    // this abomination out.
    // Rationale for allowing the sleep to occur is because the ask i.e. ? will
    // occur before the http-request which would return a None.
    Thread.sleep(cfg.timeout * 1000)
    futureDelay[GetAuthScopeStack, Option[SlackAccessToken[String]]](Await.result((actor ? GetToken).mapTo[Option[SlackAccessToken[String]]], timeout.duration))
  }
 
  /**
    * Attempts to get the list of OAuth scopes from Slack via a RESTful call
    * for the current token. Assumption here is that the token is valid.
    *
    * @param cfg the configuration object that allows us to retrieve oauth-scope for the token.
    * @param token the slack token
    * @param httpService the http service
    * @return either `None` or `Some[SlackAccessToken]`
    */
  def getOAuthScope(cfg: SlackAuthScopeConfig[String], token : Token, httpService : HttpService)
                   (implicit actorSystem: ActorSystem, actorMat: ActorMaterializer) : Eff[GetAuthScopeStack, Option[SlackAccessToken[String]]] = for {
    scope <- getScope(cfg, token, httpService)
    _     <- tell[GetAuthScopeStack,String](s"Slack oauth-scope is retrieved for this token.")
  } yield scope

}
