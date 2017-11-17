package providers.slack.algebra

/**
  * Contains the algebra for obtaining the data related to reading channel data
  * via Slack
  *
  * @author Raymond Tay
  * @version 1.0
  */

object Channels {
  import cats._, data._
  import org.atnos.eff._

  type Stack = Fx.fx3[TimedFuture, WriterString, ReaderSlackAccessToken]

}
