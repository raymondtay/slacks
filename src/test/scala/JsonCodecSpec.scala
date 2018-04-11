package providers.slack.models


import io.circe._, io.circe.parser._, io.circe.syntax._, io.circe.generic.semiauto._
import cats._, implicits._

import org.specs2._
import org.specs2.specification.AfterAll
import org.scalacheck._
import Arbitrary._
import Gen._
import Prop.{forAll, throws, AnyOperators}

import slacks.core.config.Config
import slacks.core.program.SievedMessages
import scala.collection.JavaConverters._


/**
  * The APIs that are like containerOf, listOf (its cousins and derivatives) are suffering from this:
  * https://github.com/rickynils/scalacheck/issues/89
  *
  * A possible work around:
  * https://github.com/rickynils/scalacheck/pull/370
  */
object JsonCodecGenerators {

  def generateLegalSlackUserIds : Gen[String] = for {
    suffix ← alphaNumStr.suchThat(!_.isEmpty)
  } yield s"<@U${suffix}>"

  def generateThumbs360 = choose(0, 200)
  def generateThumbsPdf = oneOf("http://bogus.com/a.pdf", "","http://anotherbogus.com/444.pdf")
  def generateThumbsVid = oneOf("http://bogus.com/a.mp4", "","http://anotherbogus.com/444.mp4")

  def genUserFile : Gen[UserFile] = for {
    filetype ← arbitrary[String].suchThat(!_.isEmpty)
    id ← arbitrary[String].suchThat(!_.isEmpty)
    title ← arbitrary[String].suchThat(!_.isEmpty)
    url_private ← arbitrary[String].suchThat(!_.isEmpty)
    external_type ← arbitrary[String].suchThat(!_.isEmpty)
    timestamp ← arbitrary[Long]
    pretty_type ← arbitrary[String].suchThat(!_.isEmpty)
    name ← arbitrary[String].suchThat(!_.isEmpty)
    mimetype ← arbitrary[String].suchThat(!_.isEmpty)
    permalink ← arbitrary[String].suchThat(!_.isEmpty)
    created ← arbitrary[Long]
    mode ← arbitrary[String].suchThat(!_.isEmpty)
    thumb360 ← option(generateThumbs360)
    thumbPdf ← option(generateThumbsPdf)
    thumbVid ← option(generateThumbsVid)
  } yield UserFile(filetype, id, title, url_private, external_type, timestamp, pretty_type, name, mimetype, permalink, created, mode, thumb360, thumbPdf, thumbVid)

  def genUserFileComment : Gen[UserFileComment] = for {
    id ← arbitrary[String].suchThat(!_.isEmpty)
    timestamp ← arbitrary[Long]
    user ← arbitrary[String].suchThat(!_.isEmpty)
  } yield UserFileComment(id, timestamp, user)

  def genUserFileShareMessage : Gen[UserFileShareMessage] = for {
    tpe ← arbitrary[String].suchThat(!_.isEmpty)
    subtype ← arbitrary[String].suchThat(!_.isEmpty)
    text ← arbitrary[String].suchThat(!_.isEmpty)
    file ← genUserFile
    fileComment1 ← genUserFileComment
    fileComment2 ← genUserFileComment
    user ← arbitrary[String].suchThat(!_.isEmpty)
    bot_id ← arbitrary[String].suchThat(!_.isEmpty)
    ts ← arbitrary[String].suchThat(!_.isEmpty)
  } yield UserFileShareMessage(tpe, subtype, text, file, fileComment1 ::fileComment2 ::Nil, user, bot_id, ts, Nil)

  def genBotAttachment : Gen[BotAttachment] = for {
    fallback ← arbitrary[String].suchThat(!_.isEmpty)
    text ← arbitrary[String].suchThat(!_.isEmpty)
    pretext ← arbitrary[String].suchThat(!_.isEmpty)
    id ← arbitrary[Long]
    color ← arbitrary[String].suchThat(!_.isEmpty)
    mrkdwn_in1 ← alphaStr.suchThat(!_.isEmpty)
    mrkdwn_in2 ← alphaStr.suchThat(!_.isEmpty)
  } yield BotAttachment(fallback, text, pretext, id, color, mrkdwn_in1::mrkdwn_in2::Nil)


  def genReaction : Gen[Reaction] = for {
    name ← arbitrary[String].suchThat(!_.isEmpty)
    user1 ← alphaStr.suchThat(!_.isEmpty)
    user2 ← alphaStr.suchThat(!_.isEmpty)
  } yield Reaction(name, user1::user2::Nil)

  def genReply : Gen[Reply] = for {
    ts ← arbitrary[String].suchThat(!_.isEmpty)
    user ← arbitrary[String].suchThat(!_.isEmpty)
  } yield Reply(ts, user)

  def genBotAttachmentMessage : Gen[BotAttachmentMessage] = for {
    tpe ← arbitrary[String].suchThat(!_.isEmpty)
    user ← arbitrary[String].suchThat(!_.isEmpty)
    bot_id ← arbitrary[String].suchThat(!_.isEmpty)
    botAtt1 ← genBotAttachment
    botAtt2 ← genBotAttachment
    reac1 ← genReaction
    reac2 ← genReaction
    reply1 ← genReply
    reply2 ← genReply
    text ← arbitrary[String].suchThat(!_.isEmpty)
    ts ← arbitrary[String].suchThat(!_.isEmpty)
  } yield BotAttachmentMessage(tpe, user, bot_id, text, botAtt1::botAtt2::Nil, ts, reac1::reac2::Nil, reply1::reply2::Nil, Nil)

  def genUserAttachmentMessage : Gen[UserAttachmentMessage] = for {
    tpe ← arbitrary[String].suchThat(!_.isEmpty)
    user ← arbitrary[String].suchThat(!_.isEmpty)
    text ← arbitrary[String].suchThat(!_.isEmpty)
    ts ← arbitrary[String].suchThat(!_.isEmpty)
    reac1 ← genReaction
    reac2 ← genReaction
    reply1 ← genReply
    reply2 ← genReply
  } yield UserAttachmentMessage(tpe, user, text, List(Json.arr(Json.fromString("test"))), ts, reac1::reac2::Nil, reply1::reply2::Nil, Nil)

  val genFileComment : Gen[FileComment] = for {
    tpe ← arbitrary[String].suchThat(!_.isEmpty)
    subtype  ← arbitrary[String].suchThat(!_.isEmpty)
    text  ← arbitrary[String].suchThat(!_.isEmpty)
    user  ← arbitrary[String].suchThat(!_.isEmpty)
    comment  ← arbitrary[String].suchThat(!_.isEmpty)
    mentions  ← listOfN(5, generateLegalSlackUserIds)
    reactions  ← listOfN(5, genReaction)
  } yield FileComment(tpe, subtype, text, user, comment, mentions, reactions, "123123.123123")

  implicit val arbGenUserFile = Arbitrary(genUserFile)
}

class JsonCodecSpecs extends mutable.Specification with ScalaCheck {override def is = sequential ^ s2"""
  Generate 'UserFile' object as valid json $genUserFileJson
  """

  def genUserFileJson = {
    import JsonCodecGenerators.arbGenUserFile
    prop { (msg: UserFile) ⇒
      msg.asJson(JsonCodec.slackUserFileEnc) must not beNull
    }.set(minTestsOk = 1)
  }

}

