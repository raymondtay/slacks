package slacks.core.parser

import org.specs2._
import org.scalacheck._
import com.typesafe.config._
import Arbitrary.{arbString ⇒ _, _} // suppressing the default `arbString` generator
import Gen._
import Prop.{forAll, throws, AnyOperators}

object Data {

  type SlackUserId = String
  type SlackUserIdInText = String
  type MultipleSlackUserIdInText = String

  val generateLegalSlackUserIds : Gen[SlackUserId] = for {
    suffix ← alphaNumStr.suchThat(!_.isEmpty)
  } yield s"<@U${suffix}>"

  val generateLegalSlackUserIdsWithNames : Gen[SlackUserId] = for {
    name   ← alphaStr.suchThat(!_.isEmpty)
    suffix ← alphaNumStr.suchThat(!_.isEmpty)
  } yield s"<@U${suffix}|${name}>"

  val generateSlackUids =
    frequency(
      (5, generateLegalSlackUserIds),
      (5, generateLegalSlackUserIdsWithNames)
    )

  val generateSlackUserIdsInRandomText : Gen[SlackUserIdInText] = for {
    prefix ← alphaStr.suchThat(!_.isEmpty)
    suffix ← alphaStr.suchThat(!_.isEmpty)
    uId ← generateSlackUids
  } yield s"${prefix} ${uId} ${suffix}"

  val generateMultipleSlackUserIdsInRandomText : Gen[SlackUserIdInText] = for {
    prefix ← alphaStr.suchThat(!_.isEmpty)
    suffix ← alphaStr.suchThat(!_.isEmpty)
    uId1 ← generateSlackUids
    uId2 ← generateSlackUids
    uId3 ← generateSlackUids
  } yield s"${prefix} ${uId1} ${suffix} ${uId2} ${suffix} ${uId3}"

  implicit val arbSlackUids  : Arbitrary[SlackUserId] = Arbitrary(generateSlackUids)
  implicit val arbSlackUidsInText  : Arbitrary[SlackUserIdInText] = Arbitrary(generateSlackUserIdsInRandomText)
  implicit val arbMultipleSlackUidsInText  : Arbitrary[MultipleSlackUserIdInText] = Arbitrary(generateMultipleSlackUserIdsInRandomText)

}

class ParserSpec extends mutable.Specification with ScalaCheck {
  val minimumNumberOfTests = 100
  import cats._, data._, implicits._, Validated._

  {
    import Data.arbSlackUids
    "User-mention Parser should be able to catch either: (a) user ids; (b) user ids with names when presented as such." >> prop { (uid: Data.SlackUserId) ⇒
    val x = UserMentions.getUserIds(uid)
    x.size must be_==(1)
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import Data.arbSlackUidsInText
    "User-mention Parser should be able to catch either: (a) user ids; (b) user ids with names when embedded in text." >> prop { (uid: Data.SlackUserIdInText) ⇒
    val x = UserMentions.getUserIds(uid)
    x.size must be_==(1)
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import Data.arbMultipleSlackUidsInText
    "User-mention Parser should be able to catch either: multiple (a) user ids; (b) user ids with names when embedded in text." >> prop { (uid: Data.MultipleSlackUserIdInText) ⇒
    val x = UserMentions.getUserIds(uid)
    x.size must be_==(3)
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

}
