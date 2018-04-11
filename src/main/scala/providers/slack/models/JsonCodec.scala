package providers.slack.models


object JsonCodec {
  import scala.util._
  import io.circe._, io.circe.generic.semiauto._
  import cats._, implicits._

  implicit val errorEnc : Encoder[SlackError] = deriveEncoder[SlackError]
  implicit val errorDec : Decoder[SlackError] = deriveDecoder[SlackError]
  implicit val responseDataEnc: Encoder[ResponseData] = deriveEncoder[ResponseData]
  implicit val responseDataDec: Decoder[ResponseData] = deriveDecoder[ResponseData]
  implicit val purposeEnc : Encoder[Purpose] = deriveEncoder[Purpose]
  implicit val purposeDec : Decoder[Purpose] = deriveDecoder[Purpose]
  implicit val topicEnc : Encoder[Topic] = deriveEncoder[Topic]
  implicit val topicDec : Decoder[Topic] = deriveDecoder[Topic]
  implicit val slackChannelEnc : Encoder[SlackChannel] = deriveEncoder[SlackChannel]
  implicit val slackChannelDec : Decoder[SlackChannel] = deriveDecoder[SlackChannel]
  implicit val slackChannelDataEnc : Encoder[SlackChannelData] = deriveEncoder[SlackChannelData]
  implicit val slackChannelDataDec : Decoder[SlackChannelData] = deriveDecoder[SlackChannelData]
  implicit val slackReactionEnc : Encoder[Reaction] = deriveEncoder[Reaction]
  implicit val slackReactionDec : Decoder[Reaction] = deriveDecoder[Reaction]
  implicit val slackBotAttachmentEnc : Encoder[BotAttachment] = deriveEncoder[BotAttachment]
  implicit val slackBotAttachmentDec : Decoder[BotAttachment] = deriveDecoder[BotAttachment]

  implicit val slackReplyDec : Decoder[Reply] = deriveDecoder[Reply]
  implicit val slackReplyEnc : Encoder[Reply] = deriveEncoder[Reply]
  implicit val slackMessage: Decoder[Message] = new Decoder[Message] {
    final def apply(c: HCursor): Decoder.Result[Message] =
      for {
        tpe     <- Monad[Id].pure(c.downField("type").as[String])
        subtype <- Monad[Id].pure(c.downField("subtype").as[String])
        ts      <- Monad[Id].pure(c.downField("ts").as[String])
        text    <- Monad[Id].pure(c.downField("text").as[String])
        botId   <- Monad[Id].pure(c.downField("bot_id").as[String])
        atts    <- Monad[Id].pure(c.getOrElse("attachments")(List.empty[BotAttachment]))
        reacs   <- Monad[Id].pure(c.getOrElse("reactions")(List.empty[Reaction]))
    } yield {
      Message(tpe, subtype, ts, text, botId, atts, reacs)
    }
  }
  implicit val slackUserDec : Decoder[User] = new Decoder[User] {
    final def apply(c: HCursor) : Decoder.Result[User] =
      for {
        userId         <- Monad[Id].pure(c.downField("id").as[String])
        teamId         <- Monad[Id].pure(c.downField("team_id").as[String])
        botId          <- Monad[Id].pure(c.getOrElse("bot_id")(""))
        user           <- Monad[Id].pure(c.getOrElse("user")(""))
        name           <- Monad[Id].pure(c.downField("name").as[String])
        deleted        <- Monad[Id].pure(c.downField("deleted").as[Boolean])
        isBot          <- Monad[Id].pure(c.downField("is_bot").as[Boolean])
        isAdmin        <- Monad[Id].pure(c.getOrElse("is_admin")(false))
        isOwner        <- Monad[Id].pure(c.getOrElse("is_owner")(false))
        isPrimaryOwner <- Monad[Id].pure(c.getOrElse("is_primary_owner")(false))
        email       <- Monad[Id].pure(c.downField("profile").getOrElse("email")(""))
        firstName   <- Monad[Id].pure(c.downField("profile").getOrElse("first_name")(""))
        realName    <- Monad[Id].pure(c.downField("profile").getOrElse("real_name")(""))
        lastName    <- Monad[Id].pure(c.downField("profile").getOrElse("last_name")(""))
        displayName <- Monad[Id].pure(c.downField("profile").getOrElse("display_name")(""))
        statusText  <- Monad[Id].pure(c.downField("profile").getOrElse("status_text")(""))
        statusEmoji <- Monad[Id].pure(c.downField("profile").getOrElse("status_emoji")(""))
        title       <- Monad[Id].pure(c.downField("profile").getOrElse("title")(""))
        skype       <- Monad[Id].pure(c.downField("profile").getOrElse("skype")(""))
        phone       <- Monad[Id].pure(c.downField("profile").getOrElse("phone")(""))
        image72     <- Monad[Id].pure(c.downField("profile").getOrElse("image_72")(""))
      } yield User(userId, teamId, name, deleted, firstName, realName, lastName, displayName, email, isBot, statusText, statusEmoji, title, skype, phone, isOwner, isPrimaryOwner, image72, isAdmin, botId, user)
  }
  implicit val slackUserEnc : Encoder[User] = deriveEncoder[User]
  implicit val slackUsersDec : Decoder[Users] = deriveDecoder[Users]
  implicit val slackUsersEnc : Encoder[Users] = deriveEncoder[Users]
  implicit val slackUserFileCommentDec : Decoder[UserFileComment] = deriveDecoder[UserFileComment]
  implicit val slackUserFileCommentEnc : Encoder[UserFileComment] = deriveEncoder[UserFileComment]
  implicit val slackMessages : Decoder[SlackMessage] = deriveDecoder[SlackMessage]

  implicit val slackTeamDec : Decoder[Team] = deriveDecoder[Team]
  implicit val slackEmojiDec : Decoder[Emoji] = deriveDecoder[Emoji]

  implicit val slackUserFileEnc: Encoder[UserFile] = new Encoder[UserFile] {
    final def apply(c: UserFile): Json = {
      var baseJsonObject : Option[JsonObject] =
        Json.obj(
          ("filetype", Json.fromString(c.filetype)),
          ("id" , Json.fromString(c.id)),
          ("title" , Json.fromString(c.title)),
          ("url_private" , Json.fromString(c.url_private)),
          ("external_type" , Json.fromString(c.external_type)),
          ("timestamp", Json.fromLong(c.timestamp)), 
          ("pretty_type" , Json.fromString(c.pretty_type)),
          ("name" , Json.fromString(c.name)),
          ("mimetype" , Json.fromString(c.mimetype)),
          ("permalink" , Json.fromString(c.permalink)),
          ("created", Json.fromLong(c.created)),
          ("mode" , Json.fromString(c.mode))).asObject

      baseJsonObject = baseJsonObject.map(base ⇒ c.thumb_360.fold(base)(t ⇒ base.add("thumb_360", Json.fromString(t))))
      baseJsonObject = baseJsonObject.map(base ⇒ c.thumb_pdf.fold(base)(t ⇒ base.add("thumb_pdf", Json.fromString(t))))
      baseJsonObject = baseJsonObject.map(base ⇒ c.thumb_video.fold(base)(t ⇒ base.add("thumb_video", Json.fromString(t))))
      baseJsonObject.fold(Json.Null)(Json.fromJsonObject(_))
    }
 }

  /* This pattern is useful when merging different jsons into a model where you need
   * something from `a` which cannot be found in `b` but neither `a` or `b` can
   * fullfill those requirements
   **/
  def extractNMerge : io.circe.Json => (io.circe.Json => Either[io.circe.DecodingFailure,providers.slack.models.Team]) =
    (team: io.circe.Json) ⇒ (emoji: io.circe.Json) ⇒ {
      val teamH = team.hcursor
      val emojiH = emoji.hcursor
      for {
        id           ← teamH.downField("team").downField("id").as[String]
        name         ← teamH.downField("team").downField("name").as[String]
        domain       ← teamH.downField("team").downField("domain").as[String]
        email_domain ← teamH.downField("team").downField("email_domain").as[String]
        image_132    ← teamH.downField("team").downField("icon").downField("image_132").as[String]
        emojis       ← emojiH.downField("emoji").as[Map[String,String]]
      } yield Team(id, name, domain, email_domain, image_132, emojis = emojis.toList.map(p ⇒ Emoji(p._1, p._2)))
  }
}

