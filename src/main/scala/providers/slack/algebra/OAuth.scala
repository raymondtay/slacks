package providers.slack.algebra

/**
  * Contains the algebra for negotiating the Slack's OAuth Workflow
  * The state is as follows:
  * - store the client_id and client_secret
  * - lift the current client_id
  * - store the code and state returned by Slack's servers
  * - Get the stored code or state
  * @author Raymond Tay
  * @version 1.0
  */

sealed trait OAuth[A]
case class ClientCredentials[T](clientId: String, clientSecretKey: Option[String]) extends OAuth[Unit]
case class ClientId[T]() extends OAuth[Option[String]]
case class StoreCode[T](code: String, state: Option[String]) extends OAuth[Unit]
case class GetCode[T]() extends OAuth[Option[String]]
case class GetState[T]() extends OAuth[Option[String]]

object OAuth {
  import cats._, data._
  import org.atnos.eff._

  type ClientId = String
  type ClientSecretKey = String

  /* Asking the context for the 2-tuple of client-id and some secret key if
   * present */
  type ReaderCredentials[A] = Reader[(ClientId, Option[ClientSecretKey]), A]

  /* Produce a side-effect of logging events. */
  type WriterString[A] = Writer[String, A]

  type Stack = Fx.fx3[WriterString, ReaderCredentials, Eval]

}
