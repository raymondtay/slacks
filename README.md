# slacks

[![CircleCI](https://circleci.com/gh/raymondtay/slacks/tree/master.svg?style=svg)](https://circleci.com/gh/raymondtay/slacks/tree/master)

A mini library for Slack

# dependencies

At the moment, `slacks` uses `cats`, `eff`, `fastparse`, `scalacheck` and
`specs2` and the author is grateful the contributors of those libraries.

*Note* : Not using the recent version `4.5.0` of `Eff` as its not fully
compatible with version `1.0.0-RC1` of `Cats`; hence we're sticking to `0.9.0`
for `Cats`. Stay tuned for updates.

# How to use 

You would normally leverage slacks right after you have authorized via Slack's
OAuth model.

(1) Export the environment variables `SLACK_CLIENT_ID` and `SLACK_SECRET_KEY`
if you feel uncomfortable about exposing these credentials in the configuration
file `application.conf`

# License

The MIT License (MIT)

Copyright (c) 2017 Raymond Tay (raymondtay1974@gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

