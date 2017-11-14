package slacks.core.program

/**
  * This is the interpreter for the Slack Algebra
  * @author Raymond Tay
  * @version 1.0
  */

import providers.slack.algebra._
import slacks.core.config.SlackAccessConfig

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.util.{ByteString, Timeout}

object OAuthInterpreter {
  import cats._, data._, implicits._
  import org.atnos.eff._
  import org.atnos.eff.all._
  import org.atnos.eff.future._
  import org.atnos.eff.syntax.all._
  import org.atnos.eff.syntax.future._

  import OAuth._

  type _readerCredentials[R] = ReaderCredentials |= R
  type _writerString[R] = WriterString |= R

  private def askFromSlack(cfg : SlackAccessConfig[String],
                           code : SlackCode,
                           credential : SlackCredentials,
                           httpService : HttpService)
                          (implicit actorSystem : ActorSystem, actorMat: ActorMaterializer) = {
    import akka.pattern.{ask, pipe}
    import scala.concurrent._
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val actor = actorSystem.actorOf(Props(new SlackActor(cfg, code, credential, httpService)), "slack-actor")
    implicit val timeout = Timeout(cfg.timeout seconds)
    // TODO: Once Eff-Monad upgrades to allow waitFor, retryUntil, we will take
    // this abomination out.
    // Rationale for allowing the sleep to occur is because the ask i.e. ? will
    // occur before the http-request which would return a None.
    Thread.sleep(2000)
    futureDelay[Stack, Option[SlackToken]](Await.result((actor ? GetToken).mapTo[Option[SlackToken]], timeout.duration))
  }
                       
  def getClientCredentials : Eff[CredentialsStack, Option[ClientSecretKey]] = for {
    datum  <- ask[CredentialsStack, (ClientId,Option[ClientSecretKey])]
    _      <- tell[CredentialsStack,String](s"Credentials is retrieved.")
  } yield datum._2

  def getSlackAccessToken(cfg: SlackAccessConfig[String], 
                          code : SlackCode,
                          httpService : HttpService)(implicit actorSystem: ActorSystem, actorMat: ActorMaterializer) : Eff[Stack, Option[SlackToken]] = for {
    credentials  <- ask[Stack, (ClientId,Option[ClientSecretKey])]
    _            <- tell[Stack,String](s"Credentials is retrieved.")
    tokenF       <- askFromSlack(cfg, code, credentials, httpService)
  } yield tokenF

}
