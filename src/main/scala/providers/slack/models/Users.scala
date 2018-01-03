package providers.slack.models

case class Users(ok : Boolean, members: List[User], cache_ts : Long, response_metadata : Option[ResponseData], offset: String)
case class User(
  id : String,
  team_id: String,
  name : String,
  deleted : Boolean,
  first_name : String,
  real_name : String,
  last_name : String,
  display_name : String,
  email : String,
  is_bot : Boolean,
  status_text : String,
  status_emoji : String,
  title : String,
  skype : String,
  phone : String,
  is_owner : Boolean,
  is_primary_owner : Boolean,
  image_72 : String,
  is_admin : Boolean,
  bot_id : String,
  user : String
  )
