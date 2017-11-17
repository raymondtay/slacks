package providers.slack

// type aliases and other niceties
//
package object algebra {
  import slacks.core.config.SlackAccessConfig
  import providers.slack.models.SlackAccessToken
  import com.typesafe.config._
  import cats._, data._
  import org.atnos.eff._

  // 
  // Slack's data model 
  //
  type ClientId = String // client's id
  type ClientSecretKey = String // secret key for client
  type SlackToken = String // access token (does not expire) from Slack
  type SlackCode = String // temporary code token from Slack
  type SlackCredentials = (ClientId, Option[ClientSecretKey])

  /* Asking the context for the 2-tuple of client-id and some secret key if
   * present */
  type ReaderCredentials[A] = Reader[(ClientId, Option[ClientSecretKey]), A]

  /* Get the configuration object */
  type ReaderConfig[A] = Reader[SlackAccessConfig[String], A]

  /* Get the slack code */
  type ReaderSlackCode[A] = Reader[SlackCode, A]

  /* Produce a side-effect of logging events. */
  type WriterString[A] = Writer[String, A]

  /* Get the slack access token */
  type ReaderSlackAccessToken[A] = Reader[SlackAccessToken[String], A]

}
