package providers.slack.models

import slacks.core.models.{Model, Token}

// encapsulates the object returned by Slack when asking (first time only) for
// the access token
case class SlackAccessToken[A](access_token: Token, scope: List[A]) extends Model[A]

