package providers.slack.models


object JsonCodec {
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
  implicit val slackUserAttachmentEnc : Encoder[UserAttachment] = deriveEncoder[UserAttachment]

  // custom decoder here instead of leveraging automatic derivation because of
  // some attributes of the json data from slack's response changes s.t. fields
  // in the returned json might be missing.
  implicit val slackUserAttachmentDec : Decoder[UserAttachment] = new Decoder[UserAttachment] {
    final def apply(c: HCursor) : Decoder.Result[UserAttachment] = 
      for {
        fallback    <- Monad[Id].pure(c.downField("fallback").as[String])
        serviceIcon <- Monad[Id].pure(c.downField("service_icon").as[String])
        fromUrl     <- Monad[Id].pure(c.downField("from_url").as[String])
        txt         <- Monad[Id].pure(c.downField("text").as[String])
        titleLink   <- Monad[Id].pure(c.downField("title_link").as[String])
        id          <- Monad[Id].pure(c.downField("id").as[Long])
        serviceName <- Monad[Id].pure(c.downField("service_name").as[String])
        title       <- Monad[Id].pure(c.downField("title").as[String])
        thumbUrl    <- Monad[Id].pure(c.getOrElse("thumb_url")(""))
        thumbWidth  <- Monad[Id].pure(c.getOrElse("thumb_width")(0))
        thumbHeight <- Monad[Id].pure(c.getOrElse("thumb_height")(0))
      } yield UserAttachment(fallback, serviceIcon, fromUrl, txt, titleLink, id, serviceName, title, thumbUrl, thumbWidth, thumbHeight)
  }
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
  implicit val slackMessages : Decoder[SlackMessage] = deriveDecoder[SlackMessage]

}
