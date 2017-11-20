package providers.slack.models


object JsonCodec {
  import io.circe._, io.circe.generic.semiauto._

  implicit val responseDataDec: Decoder[ResponseData] = deriveDecoder[ResponseData]
  implicit val purposeDec : Decoder[Purpose] = deriveDecoder[Purpose]
  implicit val topicDec : Decoder[Topic] = deriveDecoder[Topic]
  implicit val slackChannelDec : Decoder[SlackChannel] = deriveDecoder[SlackChannel]
  implicit val slackChannelData : Decoder[SlackChannelData] = deriveDecoder[SlackChannelData]
  implicit val slackReaction : Decoder[Reaction] = deriveDecoder[Reaction]
  implicit val slackAttachment : Decoder[Attachment] = deriveDecoder[Attachment]
  implicit val slackMessage: Decoder[Message] = new Decoder[Message] {
    final def apply(c: HCursor): Decoder.Result[Message] =
      for {
        tpe     <- c.downField("type").as[String]
        subtype <- c.downField("subtype").as[String]
        ts      <- c.downField("ts").as[String]
        text    <- c.downField("text").as[String]
        botId   <- c.downField("bot_id").as[String]
        atts    <- c.getOrElse("attachments")(List.empty[Attachment])
        reacs   <- c.getOrElse("reactions")(List.empty[Reaction])
    } yield {
      Message(tpe, subtype, ts, text, botId, atts, reacs)
    }
  }
  implicit val slackMessages : Decoder[SlackMessage] = deriveDecoder[SlackMessage]

}
