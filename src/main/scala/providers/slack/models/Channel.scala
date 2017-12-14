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
  attachments : List[BotAttachment], // list of attachments
  reactions : List[Reaction]      // list of reactions to message
)

case class BotAttachment(
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

case class Reply(ts: String, user: String) // "ts" - the timestamp in string format, "user" - the slack user id

case class BotAttachmentMessage(
  `type`: String,
  user: String = "",
  bot_id: String,
  text: String,
  attachments: List[BotAttachment],
  ts: String,
  reactions: List[Reaction],
  replies: List[Reply])

case class UserAttachment(
  fallback: String,
  service_icon : String,
  from_url : String,
  text : String, 
  title_link : String,
  id : Long,
  service_name : String,
  title : String,
  thumb_url : String,
  thumb_width : Int,
  thumb_height : Int
  )

case class UserAttachmentMessage(
  `type`: String,
  user: String,
  bot_id: String = "",
  text: String,
  attachments: List[UserAttachment],
  ts: String,
  reactions: List[Reaction],
  replies: List[Reply])

case class UserFileShareMessage(
  `type`: String,
  subtype: String,
  text : String,
  id : String,
  created : Long,
  timestamp : String, 
  name : String,
  title : String,
  filetype : String,
  pretty_type : String,
  user : String,
  is_external : Boolean,
  external_type : String,
  username : String,
  thumb_1024 : String,
  permalink : String,
  comments : List[UserFileComment], 
  ts : String
  )

case class UserFileComment(
  id : String,
  timestamp : Long,
  user : String
  )
