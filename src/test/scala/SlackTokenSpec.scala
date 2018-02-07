package slacks.core.models

import org.specs2._
import org.scalacheck._
import com.typesafe.config._
import Arbitrary._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}

class SlackTokenSpec extends mutable.Specification with ScalaCheck {
  val  minimumNumberOfTests = 100
  import cats._, data._, implicits._, Validated._

  {
    s"All slack tokens must start with the prefixes: [${slackPrefixes.mkString(",")}], otherwise it is an error." >> prop { (data: String) ⇒
      decipher(data) must beLeft
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    arbitrary[String].suchThat(!_.isEmpty)
    s"All slack tokens that have the prefixes: [${slackPrefixes.mkString(",")}] will NOT be registered as errors." >> forAll(arbitrary[String].suchThat(!_.isEmpty)) { (data: String) ⇒
      val results : List[Either[Throwable,Token]] = for {
        prefix <- slackPrefixes.toList
      } yield decipher(prefix + data)

      results must contain(beRight((t:Token) ⇒ t.prefix.size must be_>(0))).forall
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

}
