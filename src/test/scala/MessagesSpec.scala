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

  val fileComment =
   """
   {
   "type": "message",
   "subtype": "file_comment",
   "text": "<@U17AGMDU4> commented on <@U1LPSJQR0>\u2019s file <https:\/\/bogus.slack.com\/files\/U1LPSJQR0\/F9RQ5DSRL\/image_uploaded_from_ios.jpg|Slack for iOS Upload.jpg>: Thanks <@U1LPSJQR0> for flagging this and reminding @everyone",
   "file": { },
   "comment": {
     "id": "Fc9R2Y28VB",
     "created": 1521180551,
     "timestamp": 1521180551,
     "user": "U17AGMDU4",
     "is_intro": false,
     "comment": "Thanks <@U1LPSJQR0> for flagging this and reminding @everyone"
   },
   "is_intro": false,
   "ts": "1521180551.000091"
   }
   """
   val fileCommentWithReactionsToComment = """
   {
      "type": "message",
      "subtype": "file_comment",
      "text": "<@U43BF9474> commented on <@U55BF9483>’s file <https://dummy.slack.com/files/U37BF9497/F9VKDVDAA/img_20180323_101614.jpg|Yum...>: quiche aint bad. I like wholemeal bread sandwiches too",
      "file": {},
      "comment": {
        "id": "Fc9UMU07KM",
        "created": 1521771931,
        "timestamp": 1521771931,
        "user": "U37BF9490",
        "is_intro": false,
        "comment": "quiche aint bad. I like wholemeal bread sandwiches too",
        "reactions": [
          {
          "name": "pickle_rick",
          "users": [ "U2FQG2G9F" ],
          "count": 1
          }
        ]
      },
      "is_intro": false,
      "ts": "1521771931.000213"
   }
   """
  val generateFileCommentSlackMessageWithNoReactions : Gen[Json] = {
    val fileCommentJson = {
      import _root_.io.circe.parser._
      import cats._, implicits._
      List(fileComment).map(parse(_).getOrElse(Json.Null))
    }

    for {
      msg ← oneOf(fileCommentJson)
    } yield msg 
  }

  val generateFileCommentSlackMessageWithReactions : Gen[Json] = {
    val fileCommentJson = {
      import _root_.io.circe.parser._
      import cats._, implicits._
      List(fileCommentWithReactionsToComment).map(parse(_).getOrElse(Json.Null))
    }

    for {
      msg ← oneOf(fileCommentJson)
    } yield msg 
  }

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
      subtype ← oneOf("bot_message" :: Nil)
      data    ← alphaStr.suchThat(!_.isEmpty)
      user1   ← slacks.core.parser.Data.generateLegalSlackUserIds
      user2   ← slacks.core.parser.Data.generateLegalSlackUserIdsWithNames
    } yield Json.obj(
      ("type",    Json.fromString("message")), 
      ("subtype", Json.fromString(subtype)),
      ("text", Json.fromString(user1 + data + user2))
    ).asJson
  }

  val generateBlacklistedSlackMessagesWithUserMentions : Gen[Json] = {
    import _root_.io.circe.syntax._
    for {
      subtype ← oneOf("file_share", "file_comment")
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
  implicit val arbBlacklistedMessagesWithUserMentions  : Arbitrary[Json] = Arbitrary(generateBlacklistedSlackMessagesWithUserMentions)
  implicit val arbFileCommentMessageWithNoReactions : Arbitrary[Json] = Arbitrary(generateFileCommentSlackMessageWithNoReactions)
  implicit val arbFileCommentMessageWithReactions : Arbitrary[Json] = Arbitrary(generateFileCommentSlackMessageWithReactions)
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
    "When user mentions are detected in the 'text' field but they are whitelisted message subtypes, they will be captured" >> prop { (message: Json) ⇒
      val result = Messages.extractUserMentions(message)
      result must not be empty
      result.size must be_==(2)
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import Data.arbBlacklistedMessagesWithUserMentions
    "When user mentions are detected in the 'text' field but they are blacklisted message subtypes, they will NOT be captured" >> prop { (message: Json) ⇒
      val result = Messages.extractUserMentions(message)
      result must beEmpty
      result.size must be_==(0)
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import Data.arbFileCommentMessageWithNoReactions
    "Processing 'file_comment'-kind message should capture (a) user who made the comment; (b) user mentions; (c) actual comment." >> prop { (message: Json) ⇒
      val result = Messages.getUserMentionsInFileComments(message)
      result must not be empty
      result.size must be_==(1)
      result.extract.reactions.size must be_==(0)
      result.extract.mentions.size must be_==(1)
      result.extract.text must not be empty
      result.extract.user must not be empty
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

  {
    import Data.arbFileCommentMessageWithReactions
    "Processing 'file_comment'-kind message should capture (a) user who made the comment; (b) user mentions; (c) actual comment and (d) reactions to comments" >> prop { (message: Json) ⇒
      val result = Messages.getUserMentionsInFileComments(message)
      result must not be empty
      result.size must be_==(1)
      result.extract.reactions.size must be_==(1)
      result.extract.mentions.size must be_==(0)
      result.extract.text must not be empty
      result.extract.user must not be empty
    }.set(minTestsOk = minimumNumberOfTests, workers = 1)
  }

}
