package providers.slack.models


object JsonCodec {
  import io.circe._, io.circe.generic.semiauto._

  implicit val responseDataDec: Decoder[ResponseData] = deriveDecoder[ResponseData]
  implicit val purposeDec : Decoder[Purpose] = deriveDecoder[Purpose]
  implicit val topicDec : Decoder[Topic] = deriveDecoder[Topic]
  implicit val slackChannelDec : Decoder[SlackChannel] = deriveDecoder[SlackChannel]
  implicit val slackChannelData : Decoder[SlackChannelData] = deriveDecoder[SlackChannelData]

}
