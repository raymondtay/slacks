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
      scope.url = "http://api.slack.com/blahblah"
      scope.params = ["token"]
      scope.timeout = 4
    }""" ,
    """
    slacks.oauth.auth {
      url = "https://slack.com/oauth/authorize"
      params = ["client_id", "redirect_uri?"]
      scope.url = "http://api.slack.com/blahblah"
      scope.params = ["token"]
      scope.timeout = 4
    }""" ,
    """
    slacks.oauth.auth {
      url = "https://slack.com/oauth/authorize"
      params = ["client_id", "scope", "redirect_uri?", "state?", "team?"]
      scope.url = "http://api.slack.com/blahblah"
      scope.params = ["token"]
      scope.timeout = 4
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
      scope.url = "http://api.slack.com/blahblah"
      scope.params = ["token"]
      scope.timeout = 4
    }""" ,
    """
    slacks.oauth.auth {
      url = "http://finance.yahoo.com/q/h?s=^IXIC"
      params = ["", ""]
      scope.url = "http://api.slack.com/blahblah"
      scope.params = ["token"]
      scope.timeout = 4
    }""" ,
    """
    slacks.oauth.auth {
      url = "https://slack.com/oauth/authorize"
      params = ["", ""]
      scope.url = "http://api.slack.com/blahblah"
      scope.params = ["token"]
      scope.timeout = 4
    }""" ,
    """
    slacks.oauth.auth {
      url = "https://slack.com/oauth/authorize"
      scope.url = "http://api.slack.com/blahblah"
      scope.params = ["token"]
      scope.timeout = 4
    }""" ,
    """
    slacks.oauth.auth {
      scope.url = "http://api.slack.com/blahblah"
      scope.params = ["token"]
      scope.timeout = 4
    }
    """).map(cfg ⇒ ConfigFactory.parseString(cfg))

  val missingData1 = List("""
    slacks.oauth.auth {
      params = []
      scope.url = "http://api.slack.com/blahblah"
      scope.params = ["token"]
      scope.timeout = 4
    }
    """,
    """
     slacks.oauth.auth {
      url = ""
      scope.url = "http://api.slack.com/blahblah"
      scope.params = ["token"]
      scope.timeout = 4
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
    """).map(s ⇒ ConfigFactory.parseString(s))

  val badCredCfg2 = List(
    """
    slacks.oauth.credential {
      clientid = "aaa"
    }
    """).map(s ⇒ ConfigFactory.parseString(s))

  val badCredCfg3 = List(
    """
    slacks.oauth.access {
      url = "https://slack.com/api/oauth.access"
      params = ["client_id", "client_secret", "code", "redirect_uri?"]
    }
    """).map(s ⇒ ConfigFactory.parseString(s))

  val badCredCfg4 = List(
    """
    slacks.oauth.access {
      timeout = 4
      params = ["client_id", "client_secret", "code", "redirect_uri?"]
    }
    """).map(s ⇒ ConfigFactory.parseString(s))

  val badCredCfg5 = List(
    """
    slacks.oauth.access {
      url = "https://slack.com/api/oauth.access"
      timeout = 4
    }
    """).map(s ⇒ ConfigFactory.parseString(s))

  val badCredCfg6 = List(
    """
    slacks.oauth.access {
      url = "invalid-url format"
      params = ["client_id", "client_secret", "code", "redirect_uri?"]
      timeout = 4
    }""",
    """
    slacks.oauth.access {
      url = "http://"
      params = ["client_id", "client_secret", "code", "redirect_uri?"]
      timeout = 4
    }
    """).map(s ⇒ ConfigFactory.parseString(s))

  val badCredCfg7 = List(
    """
    slacks.oauth.access {
      url = "http://abc.com"
      params = ["12", "optional_param?"]
      timeout = 4
    }""",
    """
    slacks.oauth.access {
      url = "http://abc.com"
      params = ["!!", "Aba!", "aba112", "optional_param?"]
      timeout = 4
    }
    """).map(s ⇒ ConfigFactory.parseString(s))

  val badUserMentionsCfg = List(
    """
    slacks.usermentions.blacklist {
    }""",
    """
    slacks.usermentions.blacklist {
      messagetypes=3
    }
    """).map(s ⇒ ConfigFactory.parseString(s))

  val goodUserMentionsCfg = List(
    """
    slacks.usermentions.blacklist {
      messagetypes=[]
    }""",
    """
    slacks.usermentions.blacklist {
      messagetypes=["a","b"]
    }
    """).map(s ⇒ ConfigFactory.parseString(s))

  val genBadUserMentionsCfg = for { cfg <- oneOf(badUserMentionsCfg) } yield cfg
  val genGoodUserMentionsCfg = for { cfg <- oneOf(goodUserMentionsCfg) } yield cfg
  val genGoodConfig1 = for { cfg <- oneOf(goodCfgs1) } yield cfg
  val genGoodConfig2 = for { cfg <- oneOf(goodCfgs2) } yield cfg
  val genGoodCredConfig1 = for { cfg <- oneOf(goodCredCfg1) } yield cfg
  val genBadCredConfig1 = for { cfg <- oneOf(badCredCfg1) } yield cfg
  val genBadCredConfig2 = for { cfg <- oneOf(badCredCfg2) } yield cfg
  val genBadCredConfig3 = for { cfg <- oneOf(badCredCfg3) } yield cfg
  val genBadCredConfig4 = for { cfg <- oneOf(badCredCfg4) } yield cfg
  val genBadCredConfig5 = for { cfg <- oneOf(badCredCfg5) } yield cfg
  val genBadCredConfig6 = for { cfg <- oneOf(badCredCfg6) } yield cfg
  val genBadCredConfig7 = for { cfg <- oneOf(badCredCfg7) } yield cfg
  val genBadConfig = for { cfg <- oneOf(badCfgs) } yield cfg
  val genMissingData1Config = for { cfg <- oneOf(missingData1) } yield cfg
  val genMissingData2Config = for { cfg <- oneOf(missingData2) } yield cfg

  implicit val arbGoodCredConfig1 = Arbitrary(genGoodCredConfig1)
  implicit val arbBadCredConfig1 = Arbitrary(genBadCredConfig1)
  implicit val arbBadCredConfig2 = Arbitrary(genBadCredConfig2)
  implicit val arbBadCredConfig3 = Arbitrary(genBadCredConfig3)
  implicit val arbBadCredConfig4 = Arbitrary(genBadCredConfig4)
  implicit val arbBadCredConfig5 = Arbitrary(genBadCredConfig5)
  implicit val arbBadCredConfig6 = Arbitrary(genBadCredConfig6)
  implicit val arbBadCredConfig7 = Arbitrary(genBadCredConfig7)
  implicit val arbGoodConfig1 = Arbitrary(genGoodConfig1)
  implicit val arbGoodConfig2 = Arbitrary(genGoodConfig2)
  implicit val arbBadConfig = Arbitrary(genBadConfig)
  implicit val arbMissingData1Config = Arbitrary(genMissingData1Config)
  implicit val arbMissingData2Config = Arbitrary(genMissingData2Config)
  implicit val invalidUserMentionsKey = Arbitrary(genBadUserMentionsCfg)
  implicit val validUserMentionsKey = Arbitrary(genGoodUserMentionsCfg)

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
    "Missing 'clientid' will be registered and caught." >> prop { (cfg: Config) ⇒
      import cats._, data._, implicits._
      ConfigValidator.validateCredentialsConfig(cfg.getConfig("slacks.oauth.credential")) match {
        case s @ Valid(_)   ⇒ s.toEither must beRight
        case e @ Invalid(_) ⇒ 
          e.toEither must beLeft{
            (x: NonEmptyList[ConfigValidation]) ⇒ 
              x.head.errorMessage must be_==(slacks.core.config.MissingSlackClientIdKey.errorMessage)
          }
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbBadCredConfig2
    "Missing 'clientsecretkey' will be registered and caught." >> prop { (cfg: Config) ⇒
      import cats._, data._, implicits._
      ConfigValidator.validateCredentialsConfig(cfg.getConfig("slacks.oauth.credential")) match {
        case s @ Valid(_)   ⇒ s.toEither must beRight
        case e @ Invalid(_) ⇒ 
          e.toEither must beLeft{
            (x: NonEmptyList[ConfigValidation]) ⇒ 
              x.head.errorMessage must be_==(slacks.core.config.MissingSlackClientSKKey.errorMessage)
          }
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbBadCredConfig3
    "Missing 'timeout' key will be registered and caught." >> prop { (cfg: Config) ⇒
      import cats._, data._, implicits._
      ConfigValidator.validateAccessConfig(cfg.getConfig("slacks.oauth.access")) match {
        case s @ Valid(_)   ⇒ s.toEither must beRight
        case e @ Invalid(_) ⇒ 
          e.toEither must beLeft{
            (x: NonEmptyList[ConfigValidation]) ⇒ 
              x.head.errorMessage must be_==(slacks.core.config.MissingTimeoutKey.errorMessage)
          }
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbBadCredConfig4
    "Missing 'url' key will be registered and caught." >> prop { (cfg: Config) ⇒
      import cats._, data._, implicits._
      ConfigValidator.validateAccessConfig(cfg.getConfig("slacks.oauth.access")) match {
        case s @ Valid(_)   ⇒ s.toEither must beRight
        case e @ Invalid(_) ⇒ 
          e.toEither must beLeft{
            (x: NonEmptyList[ConfigValidation]) ⇒ 
              x.head.errorMessage must be_==(slacks.core.config.MissingUrlKey.errorMessage)
          }
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbBadCredConfig5
    "Missing 'params' key will be registered and caught." >> prop { (cfg: Config) ⇒
      import cats._, data._, implicits._
      ConfigValidator.validateAccessConfig(cfg.getConfig("slacks.oauth.access")) match {
        case s @ Valid(_)   ⇒ s.toEither must beRight
        case e @ Invalid(_) ⇒ 
          e.toEither must beLeft{
            (x: NonEmptyList[ConfigValidation]) ⇒ 
              x.head.errorMessage must be_==(slacks.core.config.MissingParamsKey.errorMessage)
          }
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbBadCredConfig6
    "Invalid 'url' values will be registered and caught." >> prop { (cfg: Config) ⇒
      import cats._, data._, implicits._
      ConfigValidator.validateAccessConfig(cfg.getConfig("slacks.oauth.access")) match {
        case s @ Valid(_)   ⇒ s.toEither must beRight
        case e @ Invalid(_) ⇒ 
          e.toEither must beLeft{
            (x: NonEmptyList[ConfigValidation]) ⇒ 
              x.head.errorMessage must be_==(slacks.core.config.InvalidUri.errorMessage)
          }
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbBadCredConfig7
    "Non-conforming parameter values will be registered and caught." >> prop { (cfg: Config) ⇒
      import cats._, data._, implicits._
      ConfigValidator.validateAccessConfig(cfg.getConfig("slacks.oauth.access")) match {
        case s @ Valid(_)   ⇒ s.toEither must beRight
        case e @ Invalid(_) ⇒ 
          e.toEither must beLeft{
            (x: NonEmptyList[ConfigValidation]) ⇒ 
              x.head.errorMessage must be_==(slacks.core.config.ParametersMustConformToConvention.errorMessage)
          }
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbGoodConfig1
    "Valid 'url' and 'params' will be registered and caught." >> prop{ (cfg: Config) ⇒
      import cats._, data._, implicits._
      ConfigValidator.validateAuthConfig(cfg.getConfig("slacks.oauth.auth")) match {
        case s @ Valid(_)   ⇒ s.toEither must beRight
        case e @ Invalid(_) ⇒ e.toEither must beLeft
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbGoodConfig2
    "Valid 'url' and 'params' and 'timeout' will be registered and caught." >> prop{ (cfg: Config) ⇒
      import cats._, data._, implicits._
      ConfigValidator.validateAccessConfig(cfg.getConfig("slacks.oauth.access")) match {
        case s @ Valid(_)   ⇒ s.toEither must beRight
        case e @ Invalid(_) ⇒ e.toEither must beLeft
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbBadConfig
    "part-1 Invalid/missing 'url' and/or 'params' would be caught." >> prop{ (cfg: Config) ⇒
      import cats._, data._, implicits._
      ConfigValidator.validateAuthConfig(cfg.getConfig("slacks.oauth.auth")) match {
        case s @ Valid(_)   ⇒ s.toEither must beRight
        case e @ Invalid(_) ⇒ e.toEither must beLeft
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbMissingData1Config
    "part-2 Invalid/missing 'url' and/or 'params' would be caught." >> prop{ (cfg: Config) ⇒
      import cats._, data._, implicits._
      ConfigValidator.validateAuthConfig(cfg.getConfig("slacks.oauth.auth")) match {
        case s @ Valid(_)   ⇒ s.toEither must beRight
        case e @ Invalid(_) ⇒ e.toEither must beLeft
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.arbMissingData2Config
    "part-3 Invalid/missing 'url' and/or 'params' and/or 'timeout' would be caught." >> prop{ (cfg: Config) ⇒
      import cats._, data._, implicits._
      ConfigValidator.validateAccessConfig(cfg.getConfig("slacks.oauth.access")) match {
        case s @ Valid(_)   ⇒ s.toEither must beRight
        case e @ Invalid(_) ⇒ e.toEither must beLeft
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.validUserMentionsKey
    "Valid key 'messagetypes' will be registered and caught." >> prop { (cfg: Config) ⇒
      (ConfigValidator.validateUserMentionBlacklist(cfg.getConfig("slacks.usermentions.blacklist")) : @unchecked) match {
        case Valid(expected) ⇒ expected.size must beBetween(0,2)
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import ConfigData.invalidUserMentionsKey
    "Invalid or missing key 'messagetypes' will be registered and caught." >> prop { (cfg: Config) ⇒
      ConfigValidator.validateUserMentionBlacklist(cfg.getConfig("slacks.usermentions.blacklist")) match {
        case s @ Valid(_)   ⇒ s.toEither must beRight
        case e @ Invalid(_) ⇒ 
          e.toEither must beLeft{
            (x: NonEmptyList[ConfigValidation]) ⇒ 
              x.head.errorMessage must be_==(slacks.core.config.MissingUserMentionBlacklistKey.errorMessage)
          }
      }
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

}
