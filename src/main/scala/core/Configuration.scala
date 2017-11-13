package slacks.core.config

/**
  * @author Raymond Tay
  * @version 1.0
  */

import cats._, data._, implicits._
import com.typesafe.config.ConfigFactory

object Config {
  lazy val config = ConfigFactory.load()

  val authConfig = config.getConfig("slacks.oauth.auth")
  val accessConfig = config.getConfig("slacks.oauth.access")

}
