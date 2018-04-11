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

  def generateThumbs360 = oneOf("http://bogus.com/a.png", "","http://anotherbogus.com/444.jpg")
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

