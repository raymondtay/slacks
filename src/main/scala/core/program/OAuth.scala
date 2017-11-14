package slacks.core.program

/**
  * This is the interpreter for the Slack Algebra
  * @author Raymond Tay
  * @version 1.0
  */

import providers.slack.algebra._

object OAuthInterpreter {
  import cats._, data._, implicits._
  import org.atnos.eff._
  import org.atnos.eff.all._
  import org.atnos.eff.syntax.all._

  import OAuth._

  type _readerCredentials[R] = ReaderCredentials |= R
  type _writerString[R] = WriterString |= R

  def program : Eff[Stack, Option[ClientSecretKey]] = for {
    datum <- ask[Stack, (ClientId,Option[ClientSecretKey])]
    _     <- tell[Stack,String](s"Got the credentials")
  } yield datum._2

}
