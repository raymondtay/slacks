package providers.slack.models

//
// This object contains the sieve logic for validate whether the fields and
// values we are interested in can be discovered.
//
object JsonCodecLens {
  import cats._, data._, implicits._
  import io.circe.optics.JsonPath._

  private def isMessageFieldPresent : Reader[io.circe.Json, Boolean] =
    Reader{(json: io.circe.Json) ⇒ root.`type`.string.getOption(json) != None }

  private def isMessageValueMatched(value: String) : Reader[io.circe.Json, Boolean] = 
    Reader{(json: io.circe.Json) ⇒ root.`type`.string.exist(_ == value)(json) }

  private def isSubtypeMessageValueMatched(value: String) : Reader[io.circe.Json, Boolean] = 
    Reader{(json: io.circe.Json) ⇒ root.subtype.string.exist(_ == value)(json) }

  def isMessagePresentNMatched : Reader[io.circe.Json, Boolean] = Reader{ (json: io.circe.Json) ⇒
    Applicative[Id].map2(isMessageFieldPresent(json), isMessageValueMatched("message")(json))(_ && _)
  }

  def isFileFieldPresent : Reader[io.circe.Json, Boolean] = Reader{ (json: io.circe.Json) ⇒ root.file.obj.getOption(json) != None }

  private def isFileFieldIdMatched(fileId: String) : Reader[io.circe.Json, Boolean] = Reader{ (json: io.circe.Json) ⇒ root.file.id.string.exist(_ == fileId)(json) }

  def isFileFieldWithMatchingFileId(fileId: String) : Reader[io.circe.Json, Boolean] = Reader{ (json: io.circe.Json) ⇒
    Applicative[Id].map2(isFileFieldPresent(json), isFileFieldIdMatched(fileId)(json))(_ && _) 
  }

  def isSubtypePresentNMatched(value: String) : Reader[io.circe.Json, Boolean] = Reader{ (json: io.circe.Json) ⇒
    Applicative[Id].map2(isSubtypeFieldPresent(json), isSubtypeMessageValueMatched(value)(json))(_ && _)
  }

  def isUsernameFieldPresent : Reader[io.circe.Json, Boolean] =
    Reader{(json: io.circe.Json) ⇒ root.username.string.getOption(json) != None && root.username.string.exist(_ != "")(json) }

  def isAttachmentsFieldPresent : Reader[io.circe.Json, Boolean] =
    Reader{(json: io.circe.Json) ⇒ root.attachments.arr.getOption(json) != None}

  def isSubtypeFieldPresent : Reader[io.circe.Json, Boolean] =
    Reader{(json: io.circe.Json) ⇒ root.subtype.string.getOption(json) != None }

  def isBotIdFieldPresent : Reader[io.circe.Json, Boolean] =
    Reader{(json: io.circe.Json) ⇒ 
      root.bot_id.string.getOption(json) != None &&
      root.bot_id.string.exist(_ != "") != None
    }

  def getUserIdValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.user.string.getOption(json).getOrElse("empty-user-id")}

  def getUsernameValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.username.string.getOption(json).getOrElse("empty-username")}

  def getBotIdValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.bot_id.string.getOption(json).getOrElse("empty-bot-id")}

  def getFileIdValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.file.id.string.getOption(json).getOrElse("empty-file-id")}

  def getFilenameValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.file.name.string.getOption(json).getOrElse("empty-file-name")}

  def getFileTitleValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.file.title.string.getOption(json).getOrElse("empty-file-title")}

  def getFileTypeValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.file.filetype.string.getOption(json).getOrElse("empty-file-type")}

  def getFileTimestampValue : Reader[io.circe.Json, Long] =
    Reader{ (json: io.circe.Json) ⇒ root.file.timestamp.long.getOption(json).getOrElse(0L)}

  def getFileUrlPrivateValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.file.url_private.string.getOption(json).getOrElse("empty-file-url_private")}

  def getFileMimeTypeValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.file.mimetype.string.getOption(json).getOrElse("empty-file-mimetype")}

  def getFileCreatedValue : Reader[io.circe.Json, Long] =
    Reader{ (json: io.circe.Json) ⇒ root.file.created.long.getOption(json).getOrElse(0L)}

  def getFilePrettyTypeValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.file.pretty_type.string.getOption(json).getOrElse("empty-file-pretty_type")}

  def getFileModeValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.file.mode.string.getOption(json).getOrElse("empty-file-mode")}

  def getFileExternalTypeValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.file.external_type.string.getOption(json).getOrElse("empty-external_type") }

  def getSubtypeMessageValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.subtype.string.getOption(json).getOrElse("empty-message-subtype")}

  def getMessageValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.`type`.string.getOption(json).getOrElse("empty-message-type")}

  def getTextValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.text.string.getOption(json).getOrElse("empty-text")}

  def getTimestampValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.ts.string.getOption(json).getOrElse("empty-timestamp")}

  def getIsExternalValue : Reader[io.circe.Json, Boolean] =
    Reader{ (json: io.circe.Json) ⇒ root.file.is_external.boolean.getOption(json).getOrElse(false)}

  def getThumb1024Value : Reader[io.circe.Json, String] = 
    Reader{ (json: io.circe.Json) ⇒ root.file.thumb_1024.string.getOption(json).getOrElse("empty-thumb_1024")}

  def getPermalinkValue : Reader[io.circe.Json, String] = 
    Reader{ (json: io.circe.Json) ⇒ root.file.permalink.string.getOption(json).getOrElse("empty-permalink")}

  def getTeamIdValue : Reader[io.circe.Json, String] =
    Reader{ (json: io.circe.Json) ⇒ root.team.id.string.getOption(json).getOrElse("") }
}

