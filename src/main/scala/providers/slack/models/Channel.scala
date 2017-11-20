package providers.slack.models

// The 'channel' model.
// Reference : https://api.slack.com/methods/channels.list
// 
case class SlackChannelData(ok: Boolean, channels: List[SlackChannel], response_metadata: Option[ResponseData])

case class SlackChannel(id : String,
  name : String,
  is_channel: Boolean,
  created: Long,
  creator: String,
  is_archived: Boolean,
  is_general: Boolean,
  name_normalized: String,
  is_shared: Boolean,
  is_org_shared : Boolean,
  is_member : Boolean,
  is_private : Boolean,
  is_mpim : Boolean,
  members : List[String],
  topic : Topic,
  purpose : Purpose,
  previous_names : List[String],
  num_members : Long)

case class Topic(value: String, creator: String, last_set: Long)
case class Purpose(value: String, creator: String, last_set: Long)
case class ResponseData(next_cursor: String) // Slack's pagination is done via cursor

case class SlackMessage(ok : Boolean, messages : List[Message], response_metadata: Option[ResponseData])
case class Message(
  tpe: String,     // type of message
  subtpe: String,  // subtype of the message
  ts: String,      // timestamp of the message
  text: String,    // text found in channel
  bot_id: String,  // the bot's id, if any
  attachments : List[Attachment], // list of attachments
  reactions : List[Reaction]      // list of reactions to message
)

case class Attachment(
  fallback : String,
  text : String,
  pretext : String,
  id : Long,
  color : String,
  mrkdwn_in : List[String]
  )

case class Reaction(
  name : String,
  users: List[String],
  count : Long
  )
