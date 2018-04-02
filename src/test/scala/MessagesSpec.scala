package providers.slack.algebra

import _root_.io.circe._
import org.specs2._
import org.scalacheck._
import com.typesafe.config._
import Arbitrary.{arbString ⇒ _, _} // suppressing the default `arbString` generator
import Gen._
import Prop.{forAll, throws, AnyOperators}

/**
  * Caveats:
  * - Slack's API does not appear to provide an end-point for us to capture all
  *   known message types; hence, you will notice that the tests do not reflect
  *   that.
  * - An obvious side-effect is that if you enter rubbish into the
  *   configuration file, then we dont really know whether its legit. Of
  *   course, we should validators to scrape the API and validate them.
  *
  * TODO:
  * - Validators for slack's message types.
  */
object Data {

  val generateBlacklistedSubtypes = (slacks.core.config.Config.usermentionBlacklistConfig: @unchecked) match {
    case Right(cfg) ⇒ 
      for {
        subtype ← oneOf(cfg.messagetypes.toList)
      } yield subtype
  }

  val generateBlacklistedSlackMessages : Gen[Json] = {
    import _root_.io.circe.syntax._
    for {
      subtype ← generateBlacklistedSubtypes
    } yield Json.obj(
      ("type",    Json.fromString("message")), 
      ("subtype", Json.fromString(subtype))
      ).asJson
  }

  val generateWhitelistedSlackMessagesWithNoUserMentions : Gen[Json] = {
    import _root_.io.circe.syntax._
    for {
      subtype ← oneOf("bot_message", "file_share", "file_comment")
      data    ← alphaStr.suchThat(!_.isEmpty)
    } yield Json.obj(
      ("type",    Json.fromString("message")), 
      ("subtype", Json.fromString(subtype)),
      ("text", Json.fromString(data))
    ).asJson
  }

  val generateWhitelistedSlackMessagesWithUserMentions : Gen[Json] = {
    import _root_.io.circe.syntax._
    for {
      subtype ← oneOf("bot_message", "file_share", "file_comment")
      data    ← alphaStr.suchThat(!_.isEmpty)
      user1   ← slacks.core.parser.Data.generateLegalSlackUserIds
      user2   ← slacks.core.parser.Data.generateLegalSlackUserIdsWithNames
    } yield Json.obj(
      ("type",    Json.fromString("message")), 
      ("subtype", Json.fromString(subtype)),
      ("text", Json.fromString(user1 + data + user2))
    ).asJson
  }

  implicit val arbBlacklistedMessages  : Arbitrary[Json] = Arbitrary(generateBlacklistedSlackMessages)
  implicit val arbWhitelistedMessagesWithNoUserMentions  : Arbitrary[Json] = Arbitrary(generateWhitelistedSlackMessagesWithNoUserMentions)
  implicit val arbWhitelistedMessagesWithUserMentions  : Arbitrary[Json] = Arbitrary(generateWhitelistedSlackMessagesWithUserMentions)

}

class MessagesSpec extends mutable.Specification with ScalaCheck {
  val minimumNumberOfTests = 100
  import cats._, data._, implicits._, Validated._

  {
    import Data.arbBlacklistedMessages
    "Capture any black-listed message" >> prop { (message: Json) ⇒
      Messages.messageSubtypeIsInWhiteList(message) must be_==(false)
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import Data.arbWhitelistedMessagesWithNoUserMentions
    "When user mentions are absent in the 'text' field, they will not be captured" >> prop { (message: Json) ⇒
      Messages.extractUserMentions(message) must be empty
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import Data.arbWhitelistedMessagesWithUserMentions
    "When user mentions are detected in the 'text' field, they will be captured" >> prop { (message: Json) ⇒
      val result = Messages.extractUserMentions(message)
      result must not be empty
      result.size must be_==(2)
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

}
