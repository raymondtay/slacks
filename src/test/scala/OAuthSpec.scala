package slacks.core.program

import org.specs2._
import org.scalacheck._
import com.typesafe.config._
import Arbitrary._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}
import org.atnos.eff._
import org.atnos.eff.all._
import org.atnos.eff.syntax.all._
import cats._, implicits._

class OAuthSpec extends Specification with ScalaCheck { def is = s2"""

  The oauth stack can be used to
    return the client secret key    $getSecretKey
  """

  def getSecretKey = {
    import OAuthInterpreter._
    val result = program.runReader(("raymondtay", "1122ccc".some)).runEval.runWriter.run
    result._1 === "1122ccc".some
  }
}
