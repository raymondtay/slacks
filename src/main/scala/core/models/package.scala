package slacks.core

/**
  * This package contains classes that uses assertions and a way to elide them
  * during compile-time is to pass in the compiler option "-Xdisable-assertions"
  * and this is not advised, in general.
  */
package object models {

  trait Model[A] extends Serializable

  // Slack tokens have a discrimination between users, bots and workspaces.
  // The current way to tell them apart is from the documentation here:
  // https://api.slack.com/docs/token-types
  //
  import scala.util._
  import shapeless._ , record._, syntax.singleton._
  private[models] val slackPrefixes = Set("xoxp-", "xoxa-", "xoxb-")

  case class Token(prefix: String, value: String) extends Model[String] {
    assert(prefix != "" && slackPrefixes.contains(prefix), s"Invariant failed: expecting a prefix like ${slackPrefixes.mkString(",")}")
    assert(value != "" && value.size > 0, "Invariant failed: expecting a alphanumeric string")
  }

  object decipher extends Poly1 {
    import cats._, implicits._
    implicit def whichToken = at[String]{ x ⇒ Either.catchNonFatal{ Token.tupled(x.splitAt(5)) } }
  }

}
