package providers.slack.algebra 

import cats._, data._, implicits._
import slacks.core.parser.UserMentions.getUserIds
import slacks.core.config.Config
import providers.slack.models._

/**
  * Contains the functions for 
  * - looking for user mentions in Slack, together with peripheral data needed
  * - transforming that user-mentions to json objects ready to be injected.
  * @author  Raymond Tay
  * @version 1.0
  */

trait CirceJsonImplicits {
  // Note: For the semantics in [[findUserMentions]], it is the situation where
  // the use case is exclusive as the user-mentions 
  implicit val xx = new Monoid[Option[io.circe.Json]] {
    def empty = None
    def combine(x: Option[io.circe.Json], y: Option[io.circe.Json]) = (x, y) match {
      case (Some(a), None) ⇒ Some(a)
      case (None, Some(a)) ⇒ Some(a)
      case _ ⇒ None
    }
  }
}

object Messages extends CirceJsonImplicits {

  import io.circe._, io.circe.syntax._, io.circe.generic.semiauto._
  import JsonCodecLens._
  import JsonCodec.slackReactionDec
  import org.slf4j.{Logger, LoggerFactory}

  private val logger = LoggerFactory.getLogger(getClass)

  /** 
    * Mined the `file_comment` messages looking for user-mentions in
    * `reactions` and `comment` fields of the target json
    * @param json 
    * @return an object that reflects the transformed slack message of subtype:`file_comment`
    */
  def getUserMentionsInFileComments : Reader[io.circe.Json, FileComment] = Reader { (json: io.circe.Json) ⇒
    import io.circe.optics.JsonPath._ // using optics
    var fileCommentMessage =
      FileComment("message", "file_comment", "", "", "", List.empty[String], List.empty[Reaction], "")

    if (isReactionsFieldPresentInComment(json)) {
      fileCommentMessage = fileCommentMessage.copy(
        reactions = 
        root.reactions.arr.getOption(json) match {
          case Some(xs:Vector[io.circe.Json]) ⇒ xs.map(x ⇒ x.as[Reaction].getOrElse(Reaction("",Nil))).toList
          case None ⇒ List.empty[Reaction]
        })
    }

    fileCommentMessage = fileCommentMessage.copy( text     = getTextValue(json) )
    fileCommentMessage = fileCommentMessage.copy( user     = getFileCommentUserValue(json) )
    fileCommentMessage = fileCommentMessage.copy( mentions = extractFileCommentUserMentions(json) )
    fileCommentMessage = fileCommentMessage.copy( comment  = getFileCommentValue(json) )
    fileCommentMessage = fileCommentMessage.copy( ts       = getTimestampValue(json) )
    fileCommentMessage
  }

  /**
    * Parses the `file_share` message looking for values nested within the
    * `initial_comment` object. In either case, we return a `UserFileComment`
    * object
    * @param json json object enclosing `file_share`
    * @return UserFileComment object
    */
  def getFileInitialCommentInFileShareMessage : Reader[io.circe.Json, Option[UserFileComment]] = Reader { (json: io.circe.Json) ⇒
    import io.circe.optics.JsonPath._ // using optics

    getFileInitialCommentObjectValue(json)  match {
      case Left(v)  ⇒ 
        logger.warn("[getFileInitialComment] Error in decoding received json object for 'initial_comment'")       
        none
      case Right(v) ⇒
        logger.info("[getFileInitialComment] OK in decoding received json object for 'initial_comment'")       
        v.some
    }
  }

  /**
    * Get user mentions iff either `replies` or `reactions` is present
    * @param json json object
    * @return empty list or a container with user-ids
    */
  def getUserMentionsWhenRepliesOrReactionsPresent : Reader[io.circe.Json, List[String]] = Reader{ (json: io.circe.Json) ⇒
    if (isRepliesFieldPresent(json) || isReactionsFieldPresent(json))
      Messages.findUserMentions(json) else List.empty[String]
  }

  /**
    * Get user mentions when neither `replies` nor `reactions` is present
    * @param json json object
    * @return empty list or a container with user-ids
    */
  def getUserMentionsWhenRepliesNOrReactionsPresent : Reader[io.circe.Json, List[String]] = Reader{ (json: io.circe.Json) ⇒
    if (isRepliesFieldPresent(json) && !isReactionsFieldPresent(json))
      Messages.findUserMentions(json) else List.empty[String]
  }

  /**
    * Extracts user-mentions depending on whether the `subtype` is a
    * `file_share`, `file_comment`.
    * @param json json object
    * @return an empty list or a container of user-ids
    */
  val findUserMentions : Reader[Json, List[String]] = Reader{(json : io.circe.Json) ⇒
      (extractUserMentions(json),
       extractFileShareUserMentions(json),
       extractFileCommentUserMentions(json)).mapN{(a, b, c) ⇒ a |+| b |+| c }
  }

  /**
    * Injects the "target" json to the "source" json with the given keyname (as in "key")
    * @param key name of key
    * @param target json value to be associated with 'key'
    * @param source json value where key → value pair to be injected
    * @return the injected json
    */
  def inject(key: String)(target: io.circe.Json) : Reader[Json, Json] = Reader{ (source: io.circe.Json) ⇒ 
    source.mapObject((s: io.circe.JsonObject) ⇒ s.add(key, target))
  }

  /**
    * Higher-order combinator for extracting user-mentions from `file_comment`
    * and maps to a JSON object
    * @param j json object
    * @return synthesized json object
    */
  def extractFileCommentUserMentionsNMapToJson : Reader[Json, Option[Json]] = Reader{ (j: io.circe.Json) ⇒
    val (uIds, comment, user) = extractFileCommentUserMentionsNUser.run(j).extract
    transformFromFileComment(user)(comment)(uIds)
  }

  /**
    * Higher-order combinator for extracting user-mentions from `file_comment`
    * and maps to a JSON object
    * @param j json object
    * @return synthesized json object
    */
  def extractFileShareUserMentionsNMapToJson : Reader[Json, Option[Json]] = Reader{ (j: io.circe.Json) ⇒
    val (uIds, comment) = extractFileShareUserMentionsNComment.run(j).extract
    transformFromFileShare(comment)(uIds)
  }

   /**
    * Higher-order combinator for extracting user-mentions from `text`- this is
    * like a catch-22 and maps to a JSON object
    * @param j json object
    * @return synthesized json object
    */
  def extractUserMentionsNMapToJson : Reader[Json, Option[Json]] = Reader{ (j: io.circe.Json) ⇒
    val uIds = extractUserMentions.run(j).extract
    transformFromGeneral(uIds)
  }

  /**
    * Checks whether the message is in the white-list by cross-checking the
    * blacklist (see [application.conf])
    * @param json json object to be examined
    * @return a boolean value
    */
  def messageSubtypeIsInWhiteList : Reader[io.circe.Json, Boolean] = Reader { (json: io.circe.Json) ⇒
    Config.usermentionBlacklistConfig match {
      case Right(cfg) ⇒  
        if (cfg.messagetypes.contains(getSubtypeMessageValue(json))) // in the blacklist
          false
        else true
      case Left(error) ⇒ false
    }
  }

  /**
    * Transforms the input to a json object
    * @param user
    * @param comment
    * @param usermentions
    * @return json object
    */
  def transformFromFileComment(user: String)(comment: String) = Reader{ (usermentions: List[String]) ⇒
    if (usermentions.isEmpty) none
    else
    Json.obj(
      ("mentions", Json.arr(usermentions.map(Json.fromString(_)): _*)),
      ("comment", Json.fromString(comment)),
      ("user", Json.fromString(user))
    ).some
  }

  /**
    * Transforms the input to a json object
    * @param comment
    * @param usermentions
    * @return json object
    */
  def transformFromFileShare(comment: String) = Reader {
    (usermentions: List[String]) ⇒
      if (usermentions.isEmpty) none
      else
      Json.obj(
        ("mentions", Json.arr(usermentions.map(Json.fromString(_)): _*)),
        ("comment", Json.fromString(comment))
      ).some
  }

  /**
    * Transforms the input to a json object. This is a catch-22 method.
    * @param usermentions
    * @return json object
    */
  def transformFromGeneral = Reader {
    (usermentions: List[String]) ⇒
      if (usermentions.isEmpty) none
      else
      Json.obj(
        ("mentions", Json.arr(usermentions.map(Json.fromString(_)): _*))
      ).some
  }

  /**
    * Extracts (a) the user who made the comment; (b) user-mentions in the
    * comment; (c) the actual comment itself. Checks that the message subtype
    * is in the white-list.
    * @param json json object
    * @return 3-tuple (a, b, c) where a is either a empty list or filled with
    * user-ids; (b) comment (c) user who made the comment
    */
  def extractFileCommentUserMentionsNUser = Reader { (j: io.circe.Json) ⇒
     if (!messageSubtypeIsInWhiteList(j) && !isSubtypePresentNMatched("file_comment")(j)) (List.empty[String], "", "") else
     (getFileCommentValue(j) ,
      getFileCommentUserValue(j)
     ).mapN((comment, user) ⇒ (getUserIds(comment), comment, user))
  }

  def extractFileCommentUserMentions = Reader { (j: io.circe.Json) ⇒
     if (!messageSubtypeIsInWhiteList(j) && !isSubtypePresentNMatched("file_comment")(j)) List.empty[String]
     else
     getUserIds(getFileCommentValue(j))
  }

  /**
    * Extracts the user-mentions from the `initial_comment` field; extracts the
    * comment too. Checks that the message's subtype is in the white-list
    * @param json json object
    * @return 2-tuple (a, b) where a is either a empty list or filled with
    * user-ids; (b) is the comment
    */
  def extractFileShareUserMentionsNComment = Reader {
    (j: io.circe.Json) ⇒
      if(!messageSubtypeIsInWhiteList(j) && !isSubtypePresentNMatched("file_share")(j)) (List.empty[String],"") else
      (getFileInitialCommentValue(j) ,
       getFileCommentValue(j)
      ).mapN((fileInitialComment, comment) ⇒ (getUserIds(fileInitialComment), comment))
  }

   /**
    * Extracts the user-mentions from the `initial_comment` field.
    * Checks that the message's subtype is in the white-list
    * @param json json object
    * @return (a) where a is either a empty list or filled with
    * user-ids.
    */
  def extractFileShareUserMentions = Reader {
    (j: io.circe.Json) ⇒
      if (!messageSubtypeIsInWhiteList(j) && !isSubtypePresentNMatched("file_share")(j)) List.empty[String]
      else getUserIds(getFileInitialCommentValue(j))
  }

  /**
    * Extract the user-mentions from the `text` field in the json object. This
    * is a catch-22
    * Checks that the message's subtype is in the white-list
    * @param json expecting a json object
    * @return either an empty list otherwise its a list of user-ids (in slack's
    * format)
    */
  def extractUserMentions = Reader { (j: io.circe.Json) ⇒
    assert(messageSubtypeIsInWhiteList(j))
    getUserIds(getTextValue(j))
  }

}

