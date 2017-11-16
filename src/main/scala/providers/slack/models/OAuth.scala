package providers.slack.models

import slacks.core.models.Model

// encapsulates the object returned by Slack when asking (first time only) for
// the access token
case class SlackAccessToken[A](access_token: A, scope: List[A]) extends Model[A]

