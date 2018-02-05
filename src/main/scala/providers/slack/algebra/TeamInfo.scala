package providers.slack.algebra

/**
  * Contains the algebra for reading information about teams and getting the
  * emoji list.
  * via Slack
  *
  * @author Raymond Tay
  * @version 1.0
  */

object TeamInfo {
  import cats._, data._
  import org.atnos.eff._

  type Stack = Fx.fx3[TimedFuture, WriterString, ReaderSlackAccessToken]

}
