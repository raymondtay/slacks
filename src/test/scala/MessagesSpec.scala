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
   "file": {
     "id": "F9RQ5DSRL",
     "created": 1521179520,
     "timestamp": 1521179520,
     "name": "Image uploaded from iOS.jpg",
     "title": "Slack for iOS Upload.jpg",
     "mimetype": "image\/jpeg",
     "filetype": "jpg",
     "pretty_type": "JPEG",
     "user": "U1LPSJQR0",
     "editable": false,
     "size": 4766174,
     "mode": "hosted",
     "is_external": false,
     "external_type": "",
     "is_public": true,
     "public_url_shared": false,
     "display_as_bot": false,
     "username": "",
     "url_private": "https:\/\/files.slack.com\/files-pri\/T024Z5MQM-F9RQ5DSRL\/image_uploaded_from_ios.jpg",
     "url_private_download": "https:\/\/files.slack.com\/files-pri\/T024Z5MQM-F9RQ5DSRL\/download\/image_uploaded_from_ios.jpg",
     "thumb_64": "https:\/\/files.slack.com\/files-tmb\/T024Z5MQM-F9RQ5DSRL-df5d33c5e1\/image_uploaded_from_ios_64.jpg",
     "thumb_80": "https:\/\/files.slack.com\/files-tmb\/T024Z5MQM-F9RQ5DSRL-df5d33c5e1\/image_uploaded_from_ios_80.jpg",
     "thumb_360": "https:\/\/files.slack.com\/files-tmb\/T024Z5MQM-F9RQ5DSRL-df5d33c5e1\/image_uploaded_from_ios_360.jpg",
     "thumb_360_w": 360,
     "thumb_360_h": 270,
     "thumb_480": "https:\/\/files.slack.com\/files-tmb\/T024Z5MQM-F9RQ5DSRL-df5d33c5e1\/image_uploaded_from_ios_480.jpg",
     "thumb_480_w": 480,
     "thumb_480_h": 360,
     "thumb_160": "https:\/\/files.slack.com\/files-tmb\/T024Z5MQM-F9RQ5DSRL-df5d33c5e1\/image_uploaded_from_ios_160.jpg",
     "thumb_720": "https:\/\/files.slack.com\/files-tmb\/T024Z5MQM-F9RQ5DSRL-df5d33c5e1\/image_uploaded_from_ios_720.jpg",
     "thumb_720_w": 720,
     "thumb_720_h": 540,
     "thumb_800": "https:\/\/files.slack.com\/files-tmb\/T024Z5MQM-F9RQ5DSRL-df5d33c5e1\/image_uploaded_from_ios_800.jpg",
     "thumb_800_w": 800,
     "thumb_800_h": 600,
     "thumb_960": "https:\/\/files.slack.com\/files-tmb\/T024Z5MQM-F9RQ5DSRL-df5d33c5e1\/image_uploaded_from_ios_960.jpg",
     "thumb_960_w": 960,
     "thumb_960_h": 720,
     "thumb_1024": "https:\/\/files.slack.com\/files-tmb\/T024Z5MQM-F9RQ5DSRL-df5d33c5e1\/image_uploaded_from_ios_1024.jpg",
     "thumb_1024_w": 1024,
     "thumb_1024_h": 768,
     "image_exif_rotation": 1,
     "original_w": 3264,
     "original_h": 2448,
     "permalink": "https:\/\/bogus.slack.com\/files\/U1LPSJQR0\/F9RQ5DSRL\/image_uploaded_from_ios.jpg",
     "permalink_public": "https:\/\/slack-files.com\/T024Z5MQM-F9RQ5DSRL-28fc9bc7b3",
     "channels": [
       "C124Z5MQT"
     ],
     "groups": [
       
     ],
     "ims": [
       
     ],
     "comments_count": 3,
     "initial_comment": {
       "id": "Fc9RQ5FSUE",
       "created": 1521179520,
       "timestamp": 1521179520,
       "user": "U1LPSJQR0",
       "is_intro": true,
       "comment": "No one was around and the door\u2019s open on level 2"
     }
   },
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

  val generateFileCommentSlackMessage : Gen[Json] = {
    val fileCommentJson = {
      import _root_.io.circe.parser._
      import cats._, implicits._
      List(fileComment).map(parse(_).getOrElse(Json.Null))
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
  implicit val arbFileCommentMessage : Arbitrary[Json] = Arbitrary(generateFileCommentSlackMessage)
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
    import Data.arbFileCommentMessage
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

}
