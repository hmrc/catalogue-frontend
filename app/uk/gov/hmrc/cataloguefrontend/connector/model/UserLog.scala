package uk.gov.hmrc.cataloguefrontend.connector.model

import play.api.libs.functional.syntax.unlift
import play.api.libs.json.{OFormat, __}

case class UserLog(
  userName: String,
  logs       : Seq[Log]
)

object UserLog {
  val userLogFormat: OFormat[UserLog] = {
    implicit val urf = Log.format
    ( ( __ \ "userName").format[String]
      ~ ( __ \ "logs"    ).format[Seq[Log]]
      )(UserLog.apply, unlift(UserLog.unapply))
  }
}

case class Log(
  page         : String,
  visitCounter : Int
)

object Log {
  val format: OFormat[Log] = {
    ( ( __ \ "uri"  ).format[String]
      ~ ( __ \ "count").format[Int]
      )(Log.apply, unlift(Log.unapply))
  }
}
