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

  val authConfig = config.getConfig("slacks.oauth.auth")

  val accessConfig = config.getConfig("slacks.oauth.access")
}

sealed trait ConfigValidation {
  def errorMessage : String
}
case object MissingUrlKey extends ConfigValidation {
  def errorMessage = "key: 'url' is missing."
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

  def validateAuthParamsConfig(c: Config) : ValidationResult[List[ParamType[String]]] = {
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

  def validateAuthUrlConfig(c: Config) : ValidationResult[String] = {
    Try{c.getString("url")}.toOption match {
      case Some(url) ⇒
        Try{new java.net.URI(url)}.toOption match {
          case Some(uri) ⇒ url.validNel
          case None      ⇒ InvalidUri.invalidNel
        }
      case None ⇒ MissingUrlKey.invalidNel
    }
  }

}

case class SlackConfig[A](url : String, params : List[ParamType[A]])

object ConfigValidator extends ConfigValidator {

  def validateConfig(config : Config) =
    (validateAuthUrlConfig(config), 
    validateAuthParamsConfig(config)).map2((url, params) ⇒ SlackConfig(url,params))

}
