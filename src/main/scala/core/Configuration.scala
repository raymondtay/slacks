package slacks.core.config

/**
  * @author Raymond Tay
  * @version 1.0
  */

import scala.util.Try
import cats._, data._, implicits._
import com.typesafe.config._
import slacks.provider.slack.config._

object Config {
  lazy val config = ConfigFactory.load()

  val authConfig : NonEmptyList[ConfigValidation] Either SlackAuthConfig[String] = ConfigValidator.validateAuthConfig(config.getConfig("slacks.oauth.auth")).toEither

  val accessConfig : NonEmptyList[ConfigValidation] Either SlackAccessConfig[String] = ConfigValidator.validateAccessConfig(config.getConfig("slacks.oauth.access")).toEither

  val channelListConfig : NonEmptyList[ConfigValidation] Either SlackChannelListConfig[String] = ConfigValidator.validateChannelConfig(config.getConfig("slacks.api.channel.list")).toEither

  val channelReadConfig : NonEmptyList[ConfigValidation] Either SlackChannelReadConfig[String] = ConfigValidator.validateChannelReadConfig(config.getConfig("slacks.api.channel.read")).toEither

  val channelReadRepliesConfig : NonEmptyList[ConfigValidation] Either SlackChannelReadRepliesConfig[String] = ConfigValidator.validateChannelReadRepliesConfig(config.getConfig("slacks.api.conversation.read.replies")).toEither

  val usersListConfig : NonEmptyList[ConfigValidation] Either SlackUsersListConfig[String] = ConfigValidator.validateUsersListConfig(config.getConfig("slacks.api.users.list")).toEither

  val teamInfoConfig : NonEmptyList[ConfigValidation] Either SlackTeamInfoConfig[String] = ConfigValidator.validateTeamInfoConfig(config.getConfig("slacks.api.team.info")).toEither

  val emojiListConfig : NonEmptyList[ConfigValidation] Either SlackEmojiListConfig[String] = ConfigValidator.validateEmojiListConfig(config.getConfig("slacks.api.emoji.list")).toEither

  val usermentionBlacklistConfig : NonEmptyList[ConfigValidation] Either SlackBlacklistMessageForUserMentions = ConfigValidator.validateUserMentionConfig(config.getConfig("slacks.usermentions.blacklist")).toEither
}

sealed trait ConfigValidation {
  def errorMessage : String
}
case object MissingSlackClientIdKey extends ConfigValidation {
  def errorMessage = "key: 'clientid' or environment key: 'SLACK_CLIENT_ID' is missing."
}
case object MissingSlackClientSKKey extends ConfigValidation {
  def errorMessage = "key: 'clientSecretKey' or environment key: 'SLACK_SECRET_KEY' is missing."
}
case object MissingTimeoutKey extends ConfigValidation {
  def errorMessage = "key: 'timeout' is missing."
}
case object MissingUrlKey extends ConfigValidation {
  def errorMessage = "key: 'url' is missing."
}
case object MissingUserMentionBlacklistKey extends ConfigValidation {
  def errorMessage = "key: 'messagetypes' is missing."
}
case object MissingParamsKey extends ConfigValidation {
  def errorMessage = "key: 'params' is missing."
}
case object InvalidUri extends ConfigValidation {
  def errorMessage = "'url' in config file is invalid."
}

case object ParametersMustConformToConvention extends ConfigValidation {
  def errorMessage = "'params' in config file must only contain [A-Za-z_] with character '?' to mark it as optional, only."
}

sealed trait ConfigValidator {
  type ValidationResult[A] = ValidatedNel[ConfigValidation, A]

  def validateUserMentionBlacklist(c: Config) : ValidationResult[List[String]] = {
    import scala.collection.JavaConverters._
    Try{c.getStringList("messagetypes").asScala.toList}.toOption match {
      case Some(xs) ⇒ xs.map(_.trim).validNel
      case None ⇒ MissingUserMentionBlacklistKey.invalidNel
    }
  }

  def validateParams(c: Config) : ValidationResult[List[ParamType[String]]] = {
    import scala.collection.JavaConverters._
    import slacks.core.parser._

    Try{c.getStringList("params").asScala.toList}.toOption match {
      case Some(params) ⇒ 
        val paramParser = Parameter.apply[String, ParamType[String]]
        val xs = params.map(param ⇒ paramParser.parse(param)).sequence
        if(xs != None) xs.get.validNel
        else ParametersMustConformToConvention.invalidNel
      case None ⇒ MissingParamsKey.invalidNel
    }
  }

  def validateUrl(c: Config) : ValidationResult[String] = {
    Try{c.getString("url")}.toOption match {
      case Some(url) ⇒
        Try{new java.net.URI(url)}.toOption match {
          case Some(uri) ⇒ url.validNel
          case None      ⇒ InvalidUri.invalidNel
        }
      case None ⇒ MissingUrlKey.invalidNel
    }
  }

  def validateTimeout(c: Config) : ValidationResult[Long] = {
    Try{c.getLong("timeout")}.toOption match {
      case Some(timeout) ⇒ timeout.validNel
      case None ⇒ MissingTimeoutKey.invalidNel
    }
  }

  def validateClientId(c: Config) : ValidationResult[String] = {
    Try{c.getString("clientid")}.toOption match {
      case Some(cId) ⇒ cId.validNel
      case None ⇒ MissingSlackClientIdKey.invalidNel
    }
  }

  def validateClientSK(c: Config) : ValidationResult[String] = {
    Try{c.getString("clientsecretkey")}.toOption match {
      case Some(cSK) ⇒ cSK.validNel
      case None ⇒ MissingSlackClientSKKey.invalidNel
    }
  }

}

case class SlackCredentials(clientId: String, clientSecretKey: String)
case class SlackUsersListConfig[A](url : String, params : List[ParamType[A]], timeout : Long)
case class SlackChannelListConfig[A](url : String, params : List[ParamType[A]], timeout : Long)
case class SlackChannelReadConfig[A](url : String, params : List[ParamType[A]], timeout : Long) extends Serializable
case class SlackChannelReadRepliesConfig[A](url : String, params : List[ParamType[A]], timeout : Long)
case class SlackAuthConfig[A](url : String, params : List[ParamType[A]], scope: SlackAuthScopeConfig[A])
case class SlackAuthScopeConfig[A](url : String, params : List[ParamType[A]], timeout: Long)
case class SlackAccessConfig[A](url : String, params : List[ParamType[A]], timeout : Long)
case class SlackTeamInfoConfig[A](url : String, params : List[ParamType[A]], timeout : Long)
case class SlackEmojiListConfig[A](url : String, params : List[ParamType[A]], timeout : Long)
case class SlackBlacklistMessageForUserMentions(messagetypes: Set[String])

object ConfigValidator extends ConfigValidator {

  def validateUserMentionConfig(config: Config) = validateUserMentionBlacklist(config).map(xs ⇒ SlackBlacklistMessageForUserMentions(xs.toSet))

  def validateUsersListConfig(config: Config) = 
    (validateUrl(config),
     validateParams(config),
     validateTimeout(config)).mapN((url, params, timeout) ⇒ SlackUsersListConfig(url, params, timeout))

  def validateChannelReadRepliesConfig(config: Config) = 
    (validateUrl(config),
     validateParams(config),
     validateTimeout(config)).mapN((url, params, timeout) ⇒ SlackChannelReadRepliesConfig(url, params, timeout))

  def validateChannelReadConfig(config: Config) = 
    (validateUrl(config),
     validateParams(config),
     validateTimeout(config)).mapN((url, params, timeout) ⇒ SlackChannelReadConfig(url, params, timeout))

  def validateChannelConfig(config : Config) = 
    (validateUrl(config),
     validateParams(config),
     validateTimeout(config)).mapN((url, params, timeout) ⇒ SlackChannelListConfig(url, params, timeout))

  def validateCredentialsConfig(config: Config) = 
    (validateClientId(config),
     validateClientSK(config)).mapN((clientId, clientSecretKey) ⇒ SlackCredentials(clientId,clientSecretKey))

  def validateAuthConfig(config : Config) =
    (validateUrl(config), 
    validateParams(config), 
    validateUrl(config.getConfig("scope")),
    validateParams(config.getConfig("scope")),
    validateTimeout(config.getConfig("scope"))).mapN((url, params, scopeUrl, scopeParams, scopeTimeout) ⇒ SlackAuthConfig(url,params, SlackAuthScopeConfig(scopeUrl, scopeParams, scopeTimeout)))

  def validateAccessConfig(config : Config) =
    (validateUrl(config), 
    validateParams(config),
    validateTimeout(config)).mapN((url, params, timeout) ⇒ SlackAccessConfig(url,params,timeout))

  def validateTeamInfoConfig(config: Config) = 
    (validateUrl(config),
     validateParams(config),
     validateTimeout(config)).mapN((url, params, timeout) ⇒ SlackTeamInfoConfig(url, params, timeout))

  def validateEmojiListConfig(config: Config) = 
    (validateUrl(config),
     validateParams(config),
     validateTimeout(config)).mapN((url, params, timeout) ⇒ SlackEmojiListConfig(url, params, timeout))

}
