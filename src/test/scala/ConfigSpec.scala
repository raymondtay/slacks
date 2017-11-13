package slacks.core.config

import org.specs2._
import org.scalacheck._
import com.typesafe.config._
import Arbitrary._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}

object ConfigData {

  val goodCfgs = List("""
    slacks.oauth.auth {
      url = "http://google.com"
      params = ["aaa", "bbb"]
    }""" ,
    """
    slacks.oauth.auth {
      url = "https://slack.com/oauth/authorize"
      params = ["client_id", "redirect_uri?"]
    }""" ,
    """
    slacks.oauth.auth {
      url = "https://slack.com/oauth/authorize"
      params = ["client_id", "scope", "redirect_uri?", "state?", "team?"]
    }
    """).map(cfg ⇒ ConfigFactory.parseString(cfg))

  val badCfgs = List("""
    slacks.oauth.auth {
      params = ["aaa", "bbb"]
    }""" ,
    """
    slacks.oauth.auth {
      url = "http://finance.yahoo.com/q/h?s=^IXIC"
      params = ["", ""]
    }""" ,
    """
    slacks.oauth.auth {
      url = "https://slack.com/oauth/authorize"
      params = ["", ""]
    }""" ,
    """
    slacks.oauth.auth {
      url = "https://slack.com/oauth/authorize"
    }""" ,
    """
    slacks.oauth.auth {
    }
    """).map(cfg ⇒ ConfigFactory.parseString(cfg))

  val missingData1 = List("""
    slacks.oauth.auth {
      params = []
    }
    """,
    """
     slacks.oauth.auth {
      url = ""
    }
    """
    ).map(cfg ⇒ ConfigFactory.parseString(cfg))

  val genGoodConfig = for { cfg <- oneOf(goodCfgs) } yield cfg
  val genBadConfig = for { cfg <- oneOf(badCfgs) } yield cfg
  val genMissingData1Config = for { cfg <- oneOf(missingData1) } yield cfg
  implicit val arbGoodConfig = Arbitrary(genGoodConfig)
  implicit val arbBadConfig = Arbitrary(genBadConfig)
  implicit val arbMissingData1Config = Arbitrary(genMissingData1Config)

}

class ConfigSpec extends mutable.Specification with ScalaCheck {
  val  minimumNumberOfTests = 200
  import cats._, data._, implicits._, Validated._

  {
    import ConfigData.arbGoodConfig
    "Valid 'url' and 'params' will be registered and caught." >> prop{ (cfg: Config) ⇒
      ConfigValidator.validateConfig(cfg.getConfig("slacks.oauth.auth")) match {
        case Valid(slackConfig) ⇒ true
        case Invalid(_) ⇒ false
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbBadConfig
    "part-1 Invalid/missing 'url' and/or 'params' would be caught." >> prop{ (cfg: Config) ⇒
      ConfigValidator.validateConfig(cfg.getConfig("slacks.oauth.auth")) match {
        case Valid(_) ⇒ false
        case Invalid(_) ⇒ true
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbMissingData1Config
    "part-2 Invalid/missing 'url' and/or 'params' would be caught." >> prop{ (cfg: Config) ⇒
      ConfigValidator.validateConfig(cfg.getConfig("slacks.oauth.auth")) match {
        case Valid(_) ⇒ false
        case Invalid(_) ⇒ true
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

}
