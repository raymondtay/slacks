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
  implicit val slackAttachmentEnc : Encoder[Attachment] = deriveEncoder[Attachment]
  implicit val slackAttachmentDec : Decoder[Attachment] = deriveDecoder[Attachment]
  implicit val slackMessage: Decoder[Message] = new Decoder[Message] {
    final def apply(c: HCursor): Decoder.Result[Message] =
      for {
        tpe     <- Monad[Id].pure(c.downField("type").as[String])
        subtype <- Monad[Id].pure(c.downField("subtype").as[String])
        ts      <- Monad[Id].pure(c.downField("ts").as[String])
        text    <- Monad[Id].pure(c.downField("text").as[String])
        botId   <- Monad[Id].pure(c.downField("bot_id").as[String])
        atts    <- Monad[Id].pure(c.getOrElse("attachments")(List.empty[Attachment]))
        reacs   <- Monad[Id].pure(c.getOrElse("reactions")(List.empty[Reaction]))
    } yield {
      Message(tpe, subtype, ts, text, botId, atts, reacs)
    }
  }
  implicit val slackMessages : Decoder[SlackMessage] = deriveDecoder[SlackMessage]

}
