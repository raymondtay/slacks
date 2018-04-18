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
  ) extends Serializable

case class Reaction(
  name : String,
  users: List[String]
  ) extends Serializable

case class Reply(ts: String, user: String) extends Serializable // "ts" - the timestamp in string format, "user" - the slack user id

case class BotAttachmentMessage(
  `type`: String,
  subtype : String,
  username: Option[String],
  bot_id: Option[String],
  text: String,
  attachments: List[BotAttachment],
  ts: String,
  reactions: List[Reaction],
  replies: List[Reply],
  mentions: List[String]) extends Serializable

case class UserAttachmentMessage(
  `type`: String,
  user: Option[String],
  text: String,
  attachments: List[io.circe.Json],
  ts: String,
  reactions: List[Reaction],
  replies: List[Reply],
  mentions : List[String]) extends Serializable

case class UserFileShareMessage(
  `type`: String,
  subtype: String,
  text : String,
  file : UserFile,
  comments : List[UserFileComment], 
  comment : String,
  user : Option[String],
  bot_id : Option[String],
  ts: String,
  mentions : List[String]
) extends Serializable

case class UserFile(
  filetype : String,
  id : String,
  title : String,
  url_private : String,
  external_type : String,
  timestamp : Long, 
  pretty_type : String,
  name : String,
  mimetype : String,
  permalink : String,
  created : Long,
  mode : String,
  thumb_360 : Option[String],
  thumb_pdf : Option[String],
  thumb_video : Option[String]
) extends Serializable

case class UserFileComment(
  id : String,
  timestamp : Long,
  user : String
) extends Serializable

case class FileComment(
  `type`: String,
  subtype : String,
  text : String,
  user : String,
  comment : Option[String],
  mentions : List[String],
  reactions : List[Reaction],
  ts : String
) extends Serializable

