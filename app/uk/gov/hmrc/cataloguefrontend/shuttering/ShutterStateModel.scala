/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cataloguefrontend.shuttering

import java.time.LocalDateTime
import play.api.libs.json.{Format, Json, JsError, JsObject, JsValue, JsResult, JsString, JsSuccess, Reads, Writes, __}
import play.api.libs.functional.syntax._


sealed trait Environment { def asString: String }
object Environment {
  case object Production      extends Environment { val asString = "production"    }
  case object ExternalTest    extends Environment { val asString = "external test" }
  case object QA              extends Environment { val asString = "qa"            }
  case object Staging         extends Environment { val asString = "staging"       }
  case object Dev             extends Environment { val asString = "development"   }

  val values = List(Production, ExternalTest, QA, Staging, Dev)

  def parse(s: String): Option[Environment] =
    values.find(_.asString == s)

  val format: Format[Environment] = new Format[Environment] {
    override def reads(json: JsValue) =
      json.validate[String]
        .flatMap { s =>
            parse(s) match {
              case Some(env) => JsSuccess(env)
              case None      => JsError(__, s"Invalid Environment '$s'")
            }
          }

    override def writes(e: Environment) =
      JsString(e.asString)
  }
}

sealed trait ShutterStatus { def asString: String }
object ShutterStatus {
  case object Shuttered   extends ShutterStatus { val asString = "shuttered"   }
  case object Unshuttered extends ShutterStatus { val asString = "unshuttered" }

  val values = List(Shuttered, Unshuttered)

  def parse(s: String): Option[ShutterStatus] =
    values.find(_.asString == s)


  val format: Format[ShutterStatus] = new Format[ShutterStatus] {
    override def reads(json: JsValue) =
      json.validate[String]
        .flatMap { s =>
            parse(s) match {
              case Some(env) => JsSuccess(env)
              case None      => JsError(__, s"Invalid ShutterStatus '$s'")
            }
          }

    override def writes(e: ShutterStatus) =
      JsString(e.asString)
  }
}

case class ShutterState(
    name        : String
  , production  : ShutterStatus
  , staging     : ShutterStatus
  , qa          : ShutterStatus
  , externalTest: ShutterStatus
  , development : ShutterStatus
  ) {
    def statusFor(env: Environment): ShutterStatus =
      env match {
        case Environment.Production   => production
        case Environment.ExternalTest => externalTest
        case Environment.QA           => qa
        case Environment.Staging      => staging
        case Environment.Dev          => development
      }
  }

object ShutterState {

  val reads: Reads[ShutterState] = {
    implicit val ssf = ShutterStatus.format
    ( (__ \ "name"        ).read[String]
    ~ (__ \ "production"  ).read[ShutterStatus]
    ~ (__ \ "staging"     ).read[ShutterStatus]
    ~ (__ \ "qa"          ).read[ShutterStatus]
    ~ (__ \ "externalTest").read[ShutterStatus]
    ~ (__ \ "development" ).read[ShutterStatus]
    )(ShutterState.apply _)
  }
}



// -------------- Events ---------------------


sealed trait EventType { def asString: String }
object EventType {
  case object ShutterStateCreated   extends EventType { override val asString = "shutter-state-created"   }
  case object ShutterStateChange    extends EventType { override val asString = "shutter-state-change"    }
  case object KillSwitchStateChange extends EventType { override val asString = "killswitch-state-change" }


  val values = List(ShutterStateCreated, ShutterStateChange, KillSwitchStateChange)

  def parse(s: String): Option[EventType] =
    values.find(_.asString == s)

  val format: Format[EventType] = new Format[EventType] {
    override def reads(json: JsValue) =
      json.validate[String]
        .flatMap { s =>
            parse(s) match {
              case Some(et) => JsSuccess(et)
              case None     => JsError(__, s"Invalid EventType '$s'")
            }
          }

    override def writes(e: EventType) =
      JsString(e.asString)
  }
}

sealed trait ShutterCause { def asString: String }
object ShutterCause {
  case object Scheduled   extends ShutterCause { override val asString = "scheduled"   }
  case object UserCreated extends ShutterCause { override val asString = "user-shutter"}

  val values = List(Scheduled, UserCreated)

  def parse(s: String): Option[ShutterCause] =
    values.find(_.asString == s)

  val format: Format[ShutterCause] = new Format[ShutterCause] {
    override def reads(json: JsValue) =
      json.validate[String]
        .flatMap { s =>
            parse(s) match {
              case Some(et) => JsSuccess(et)
              case None     => JsError(__, s"Invalid ShutterCause '$s'")
            }
          }

    override def writes(e: ShutterCause) =
      JsString(e.asString)
  }

}

sealed trait EventData
object EventData {
  case class ShutterStateCreateData(
      serviceName: String
    ) extends EventData

  case class ShutterStateChangeData(
      serviceName: String
    , environment: Environment
    , status     : ShutterStatus
    , cause      : ShutterCause
    ) extends EventData

  case class KillSwitchStateChangeData(
      environment: Environment
    , status     : ShutterStatus
    ) extends EventData



  val shutterStatusChangeDataFormat: Format[ShutterStateChangeData] = {
    implicit val ef  = Environment.format
    implicit val ssf = ShutterStatus.format
    implicit val scf = ShutterCause.format

    ( (__ \ "serviceName").format[String]
    ~ (__ \ "environment").format[Environment]
    ~ (__ \ "status"     ).format[ShutterStatus]
    ~ (__ \ "cause"      ).format[ShutterCause]
    )( ShutterStateChangeData.apply
     , unlift(ShutterStateChangeData.unapply)
     )
  }

  val killSwitchStateChangeDataFormat: Format[KillSwitchStateChangeData] = {
    implicit val ef  = Environment.format
    implicit val ssf = ShutterStatus.format

    ( (__ \ "environment").format[Environment]
    ~ (__ \ "status"     ).format[ShutterStatus]
    )( KillSwitchStateChangeData.apply
     , unlift(KillSwitchStateChangeData.unapply
     ))
  }
}

case class ShutterEvent(
    username : String
  , timestamp: LocalDateTime
  , eventType: EventType
  , data     : EventData
  ) {
    // TODO fix this - just for migration..
    def ssData = data.asInstanceOf[EventData.ShutterStateChangeData]
  }

object ShutterEvent {

  val format: Format[ShutterEvent] = {
    implicit val ef    = Environment.format
    implicit val etf   = EventType.format
    implicit val sscdf = EventData.shutterStatusChangeDataFormat
    implicit val kscdf = EventData.killSwitchStateChangeDataFormat

    val reads: Reads[ShutterEvent] =
      ( (__ \ "username" ).read[String]
      ~ (__ \ "timestamp").read[LocalDateTime]
      ~ (__ \ "type"     ).read[EventType]
      ~ (__ \ "type"     )
            .read[EventType]
            .flatMap[EventData] { et =>
              val data = __ \ "data"
              et match {
                case EventType.ShutterStateChange    => data.read[EventData](implicitly[Reads[EventData.ShutterStateChangeData]].map[EventData](identity)) //Get round invariance of Reads[T]
                case EventType.KillSwitchStateChange => data.read[EventData](implicitly[Reads[EventData.KillSwitchStateChangeData]].map[EventData](identity))
              }
            }
      )(ShutterEvent.apply _)

    val writes: Writes[ShutterEvent] =
      ( (__ \ "username" ).write[String]
      ~ (__ \ "timestamp").write[LocalDateTime]
      ~ (__ \ "type"     ).write[EventType]
      ~ (__ \ "data"     ).write[JsValue].contramap[EventData] {
                             case s: EventData.ShutterStateChangeData    => EventData.shutterStatusChangeDataFormat.writes(s)
                             case s: EventData.KillSwitchStateChangeData => EventData.killSwitchStateChangeDataFormat.writes(s)
                           }
      )(unlift(ShutterEvent.unapply))

    Format(reads, writes)
  }

}