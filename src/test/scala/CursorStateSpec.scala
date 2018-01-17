package slacks.core.program

import org.specs2._
import org.scalacheck._
import com.typesafe.config._
import Arbitrary._
import Gen.{containerOfN, choose, pick, mapOf, listOf, oneOf}
import Prop.{forAll, throws, AnyOperators}

object StateData {

  val cStateData = List("").map(data ⇒ Cursor(data))
  val cStateData2 = List("hello", "world").map(data ⇒ Cursor(data))

  val genCStateData = for { cState <- oneOf(cStateData) } yield cState
  val genCStateData2 = for { cState <- oneOf(cStateData2) } yield cState

  implicit val arbCStateConfig  : Arbitrary[Cursor] = Arbitrary(genCStateData)
  implicit val arbCStateConfig2 : Arbitrary[Cursor] = Arbitrary(genCStateData2)

}

class CursorStateSpec extends mutable.Specification with ScalaCheck {
  val  minimumNumberOfTests = 100
  import cats._, data._, implicits._, Validated._

  {
    import StateData.arbCStateConfig
    "Initial cursor state should be empty." >> prop { (cursor: Cursor) ⇒
     cursor.getCursor must be_==("")
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import StateData.arbCStateConfig2
    "Cursor state should hold same contents as was passed." >> prop { (cursor: Cursor) ⇒
     cursor.getCursor.size must be_==(5)
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    "Updates to Cursor state should be consistent." >> prop { (data: String) ⇒
      val c = Cursor(data)
      c.updateCursor.run(c.getCursor + "!!").value
      c.getCursor must endWith("!!")
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

}
