package providers.slack.models

case class Team(
  name: String,
  domain: String,
  email_domain : String,
  image_132 : String, // this can be a java.net.URL but you have to provide a decoder for it else the compiler doesn't like it
  emojis : List[Emoji]
)

// `image` cannot be a java.net.URL because it can be an alias whose semantics
// is unknown.
case class Emoji(name : String, image: String)

