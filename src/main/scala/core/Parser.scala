package slacks.core.parser

import scala.language.higherKinds
import cats._, data._, implicits._
import fastparse.all._
import org.slf4j.{Logger, LoggerFactory}

/**
  * @author Raymond Tay
  * @version 1.0
  */

// This object contains the regex pattern for parsing user mentions in Slack's
// JSON response messages.
object UserMentions {

  // Based on Slack's user id nomenclature, a user mention would look like:
  // a) <@U12231A>
  // b) <@U123B|John>
  private val usermentions = """<@(\w+)(\|\w+)*>""".r
  def getUserIds : Kleisli[List,String,String] = Kleisli{ (text: String) ⇒
    usermentions.findAllMatchIn(text).toList.map(_.group(1))
  }
}

// Typeclass for our slack parameters which might be mandatory or optional
// as indicated by the regex in the `application.conf`
//
sealed trait Parameter[A, B, Option[B]] {
  def parse(value : A) : Option[B]
}

object Parameter {
  import slacks.provider.slack.config._
  import ParameterParserFunctions._

  def apply[A,B](implicit parser : Parameter[A,B, Option[?]]) = parser

  implicit val default : Parameter[String, ParamType[String], Option[?]] = new Parameter[String, ParamType[String],Option[?]] {
    def parse(value: String) : Option[ParamType[String]] = implicitly[Kleisli[Option,String,ParamType[String]]].run(value)
  }

}

object ParameterParserFunctions {
  import fastparse.all._
  import slacks.provider.slack.config._

  private val logger = LoggerFactory.getLogger(getClass)
  private val slackParamIdentifiers = P( (CharIn('a' to 'z') | CharIn('A' to 'Z') | CharIn('_' to '_') ).rep(min=1,max=99) )

  implicit val paramParser : Kleisli[Option,String,ParamType[String]] = Kleisli{
    (someString: String) ⇒ 
    P( slackParamIdentifiers.! ~ "?".?.! ).parse(someString) match {
      case Parsed.Success(datum, _) if (datum._2 == "?") ⇒ (OptionalParam(datum._1)).some
      case Parsed.Success(datum, _) if (datum._2 != "?") ⇒ (MandatoryParam(datum._1)).some
      case Parsed.Failure(_,_,_)    ⇒ none
    }
  }
}

