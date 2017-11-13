package slacks.provider.slack.config

/**
  * @author Raymond Tay
  * @version 1.0
  */

//
// Slack's API, like a lot of web services, defines basically two parameter
// types: (a) optional , (b) mandatory and here are the definitions.
//
//
sealed trait ParamType[A]
case class OptionalParam[A](param: A) extends ParamType[A]
case class MandatoryParam[A](param: A) extends ParamType[A]

