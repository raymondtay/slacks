# slacks
[![CircleCI branch](https://img.shields.io/circleci/project/github/RedSparr0w/node-csgo-parser/master.svg)](https://circleci.com/gh/raymondtay/slacks/tree/master)

![Cats Friendly Badge](https://typelevel.org/cats/img/cats-badge-tiny.png)

A mini library for Slack

# Supported Features

* Retrieve all users
* Retrieve team information
* Retrieve all channels
* Retrieve all posts associated with all channels

# dependencies

At the moment, `slacks` uses `cats`, `eff`, `fastparse`, `scalacheck` and
`specs2` and the author is grateful the contributors of those libraries.

*Note* : The latest updates to `slacks` is that we are supporting the latest official `Typelevel` releases so that we can progressively push our code forward. See table for latest changes:

Library name | Current version | Comments
-------------| ----------------|----------
`Cats`       | 1.0.1           | Upgrade from 0.9.0
`Eff`        | 5.0.0-RC1-20180101142835-0e4b73e| Bumped from `4.5.0` to support latest `Cats`
`Circe`      | 0.9.0 | Bumped from `0.8.0` to support latest `Cats`


# How to use 

You would normally leverage slacks right after you have authorized via Slack's
OAuth model.

(1) Export the environment variables `SLACK_CLIENT_ID` and `SLACK_SECRET_KEY`
if you feel uncomfortable about exposing these credentials in the configuration
file `application.conf`

The following two sections demonstrate what you can do with..

# Example of how to retrieve users from Slack
This code snippet, hopefully, illustrates how you could call `getAllUsers`
associated with the token you have. It also depends on whether the token has
the necessary access.

Refer to the tests in `src/test/scala` for more examples.

```scala
import slacks.core.program._
import scala.concurrent._, duration._()
import scala.concurrent.ExecutionContext.Implicits.global

val timeout = akka.util.Timeout( 2 seconds )
val accessToken = // some slack token
val cfg : NonEmptyList[ConfigValidation] Either SlackUsersListConfig = // configuration 

val (retrievedUsers, logInfo) =
  Await.result(
    getAllUsers(cfg, httpService).
      runReader(accessToken).
      runWriter.
      runSequential, timeout)
  )

```

# Example of how to gather the team id from Slack
This code snippet, hopefully, illustrates how you could call `getTeam`
associated with the token you have. It also depends on whether the token has
the necessary access.

Refer to the tests in `src/test/scala` for more examples.

```scala
import slacks.core.program._
import scala.concurrent._, duration._
import scala.concurrent.ExecutionContext.Implicits.global

val timeout = akka.util.Timeout( 2 seconds )
val accessToken = // some slack token
val teamInfoCfg : NonEmptyList[ConfigValidation] Either SlackTeamInfoConfig = // config object

val (teamId, logInfo) =
  Await.result(
    getTeam(teamInfoCfg, httpService).
      runReader(accessToken).
      runWriter.
      runSequential, timeout)
```
# License

The MIT License (MIT)

Copyright (c) 2017 Raymond Tay (raymondtay1974@gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

