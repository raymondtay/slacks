package providers.slack.algebra 

import cats._, data._, implicits._
import slacks.core.parser.UserMentions.getUserIds
import slacks.core.config.Config
import providers.slack.models._

/**
  * Contains the functions for 
  * - looking for user mentions in Slack, together with peripheral data needed
  * @author  Raymond Tay
  * @version 1.0
  */

object Messages {

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
      FileComment("message", "file_comment", "", "", None, List.empty[String], List.empty[Reaction], "")

    // implementation note: We need to fill out the "reactions" and "mentions"
    // array prior to whether we need to extract the "comment".
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
    fileCommentMessage = fileCommentMessage.copy( ts       = getTimestampValue(json) )

    if (!fileCommentMessage.reactions.isEmpty || !fileCommentMessage.mentions.isEmpty) {
     fileCommentMessage = fileCommentMessage.copy( comment  = getFileCommentValue(json) )
    }

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
    * Extracts user-mentions depending on whether the `subtype` is a
    * `file_share`, `file_comment` and not in the blacklisted message types
    * @param json json object
    * @return an empty list or a container of user-ids
    */
  def findUserMentions : Reader[Json, List[String]] = Reader{(json : io.circe.Json) ⇒
      Applicative[Id].map3(
        extractUserMentions(json),
        extractFileShareUserMentions(json),
        extractFileCommentUserMentions(json)
      )( _ |+| _ |+| _ )
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
    * Finds the user mentions in the `comment.comment` json field
    * @param json 
    * @return empty container or container of slack user ids
    */
  def extractFileCommentUserMentions : Reader[io.circe.Json, List[String]]= Reader { (j: io.circe.Json) ⇒
     if (!messageSubtypeIsInWhiteList(j) && !isSubtypePresentNMatched("file_comment")(j)) List.empty[String]
     else
     getFileCommentValue(j).fold(List.empty[String])(getUserIds(_))
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
    if (!messageSubtypeIsInWhiteList(j)) List.empty[String] else getUserIds(getTextValue(j))
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

}

