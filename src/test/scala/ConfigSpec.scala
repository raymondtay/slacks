package slacks.core.config

import org.specs2._
import org.scalacheck._
import com.typesafe.config._
import Arbitrary._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}

object ConfigData {

  val goodCfgs1 = List("""
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

  val goodCfgs2 = List("""
    slacks.oauth.access {
      url = "http://google.com"
      params = ["aaa", "bbb"]
      timeout = 1
    }""" ,
    """
    slacks.oauth.access {
      url = "https://slack.com/oauth/authorize"
      params = ["client_id", "redirect_uri?"]
      timeout = 23
    }""" ,
    """
    slacks.oauth.access {
      url = "https://slack.com/oauth/authorize"
      params = ["client_id", "scope", "redirect_uri?", "state?", "team?"]
      timeout = 32
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

  val missingData2 = List("""
    slacks.oauth.access {
      params = []
    }
    """,
    """
    slacks.oauth.access {
      timeout = 0 
    }
    """,
 
    """
     slacks.oauth.access {
      url = ""
    }
    """
    ).map(cfg ⇒ ConfigFactory.parseString(cfg))

  val goodCredCfg1 = List(
    """
    slacks.oauth.credential {
      clientid = "aaa"
      clientsecretkey = "bbb"
    }
    """).map(s ⇒ ConfigFactory.parseString(s))

  val badCredCfg1 = List(
    """
    slacks.oauth.credential {
      clientsecretkey = "bbb"
    }
    """
    ,
    """
    slacks.oauth.credential {
      clientid = "aaa"
    }
    """).map(s ⇒ ConfigFactory.parseString(s))

  val genGoodConfig1 = for { cfg <- oneOf(goodCfgs1) } yield cfg
  val genGoodConfig2 = for { cfg <- oneOf(goodCfgs2) } yield cfg
  val genGoodCredConfig1 = for { cfg <- oneOf(goodCredCfg1) } yield cfg
  val genBadCredConfig1 = for { cfg <- oneOf(badCredCfg1) } yield cfg
  val genBadConfig = for { cfg <- oneOf(badCfgs) } yield cfg
  val genMissingData1Config = for { cfg <- oneOf(missingData1) } yield cfg
  val genMissingData2Config = for { cfg <- oneOf(missingData2) } yield cfg

  implicit val arbGoodCredConfig1 = Arbitrary(genGoodCredConfig1)
  implicit val arbBadCredConfig1 = Arbitrary(genBadCredConfig1)
  implicit val arbGoodConfig1 = Arbitrary(genGoodConfig1)
  implicit val arbGoodConfig2 = Arbitrary(genGoodConfig2)
  implicit val arbBadConfig = Arbitrary(genBadConfig)
  implicit val arbMissingData1Config = Arbitrary(genMissingData1Config)
  implicit val arbMissingData2Config = Arbitrary(genMissingData2Config)

}

class ConfigSpec extends mutable.Specification with ScalaCheck {
  val  minimumNumberOfTests = 200
  import cats._, data._, implicits._, Validated._

  {
    import ConfigData.arbGoodCredConfig1
    "Valid 'clientid' and 'clientsecretkey' will be registered and caught." >> prop { (cfg: Config) ⇒
      ConfigValidator.validateCredentialsConfig(cfg.getConfig("slacks.oauth.credential")) match {
        case Valid(credential) ⇒ true
        case Invalid(_) ⇒ false
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbBadCredConfig1
    "Missing 'clientid' and/or 'clientsecretkey' will be registered and caught." >> prop { (cfg: Config) ⇒
      ConfigValidator.validateCredentialsConfig(cfg.getConfig("slacks.oauth.credential")) match {
        case Valid(credential) ⇒ false
        case Invalid(_) ⇒ true
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbGoodConfig1
    "Valid 'url' and 'params' will be registered and caught." >> prop{ (cfg: Config) ⇒
      ConfigValidator.validateAuthConfig(cfg.getConfig("slacks.oauth.auth")) match {
        case Valid(slackConfig) ⇒ true
        case Invalid(_) ⇒ false
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbGoodConfig2
    "Valid 'url' and 'params' and 'timeout' will be registered and caught." >> prop{ (cfg: Config) ⇒
      ConfigValidator.validateAccessConfig(cfg.getConfig("slacks.oauth.access")) match {
        case Valid(slackConfig) ⇒ true
        case Invalid(_) ⇒ false
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbBadConfig
    "part-1 Invalid/missing 'url' and/or 'params' would be caught." >> prop{ (cfg: Config) ⇒
      ConfigValidator.validateAuthConfig(cfg.getConfig("slacks.oauth.auth")) match {
        case Valid(_) ⇒ false
        case Invalid(_) ⇒ true
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbMissingData1Config
    "part-2 Invalid/missing 'url' and/or 'params' would be caught." >> prop{ (cfg: Config) ⇒
      ConfigValidator.validateAuthConfig(cfg.getConfig("slacks.oauth.auth")) match {
        case Valid(_) ⇒ false
        case Invalid(_) ⇒ true
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbMissingData2Config
    "part-3 Invalid/missing 'url' and/or 'params' and/or 'timeout' would be caught." >> prop{ (cfg: Config) ⇒
      ConfigValidator.validateAccessConfig(cfg.getConfig("slacks.oauth.access")) match {
        case Valid(_) ⇒ false
        case Invalid(_) ⇒ true
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

}
