package providers.slack.algebra

/**
  * Contains the algebra for
  * - extracting the users from Slack via the APIs
  * - ...
  * @author Raymond Tay
  * @version 1.0
  */
object Users {
  import cats._, data._
  import org.atnos.eff._

  type GetUsersStack = Fx.fx3[TimedFuture, WriterString, ReaderSlackAccessToken]
}

