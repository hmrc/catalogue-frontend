/*
 * Copyright 2023 HM Revenue & Customs
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

import java.time.Instant

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.PathBindable
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.ServiceName
object ShutterEnvironment {
  val format: Format[Environment] =
    new Format[Environment] {
      override def reads(json: JsValue) =
        json
          .validate[String]
          .flatMap { s =>
            Environment.parse(s) match {
              case Some(env) => JsSuccess(env)
              case None      => JsError(__, s"Invalid Environment '$s'")
            }
          }

      override def writes(e: Environment) =
        JsString(e.asString)
    }
}

enum ShutterType(val asString: String):
  case Frontend extends ShutterType("frontend")
  case Api      extends ShutterType("api"    )
  case Rate     extends ShutterType("rate"   )

object ShutterType {
  def parse(s: String): Option[ShutterType] =
    values.find(_.asString == s)

  val format: Format[ShutterType] =
    new Format[ShutterType] {
      override def reads(json: JsValue) =
        json
          .validate[String]
          .flatMap { s =>
            parse(s) match {
              case Some(st) => JsSuccess(st)
              case None     => JsError(__, s"Invalid ShutterType '$s'")
            }
          }

      override def writes(st: ShutterType) =
        JsString(st.asString)
    }

  implicit val pathBindable: PathBindable[ShutterType] =
    new PathBindable[ShutterType] {
      override def bind(key: String, value: String): Either[String, ShutterType] =
        parse(value).toRight(s"Invalid ShutterType '$value'")

      override def unbind(key: String, value: ShutterType): String =
        value.asString
    }
}

enum ShutterStatusValue(val asString: String):
  case Shuttered   extends ShutterStatusValue("shuttered"  )
  case Unshuttered extends ShutterStatusValue("unshuttered")


object ShutterStatusValue {
  def parse(s: String): Option[ShutterStatusValue] =
    values.find(_.asString == s)

  val format: Format[ShutterStatusValue] = new Format[ShutterStatusValue] {
    override def reads(json: JsValue) =
      json
        .validate[String]
        .flatMap { s =>
          parse(s) match {
            case Some(env) => JsSuccess(env)
            case None      => JsError(__, s"Invalid ShutterStatusValue '$s'")
          }
        }

    override def writes(e: ShutterStatusValue) =
      JsString(e.asString)
  }
}

enum ShutterStatus(val value: ShutterStatusValue):
  case Shuttered(
    reason              : Option[String],
    outageMessage       : Option[String],
    useDefaultOutagePage: Boolean
  ) extends ShutterStatus(ShutterStatusValue.Shuttered)
  case Unshuttered extends ShutterStatus(ShutterStatusValue.Unshuttered)

object ShutterStatus {
  val format: Format[ShutterStatus] =
    new Format[ShutterStatus] {
      override def reads(json: JsValue) = {
        implicit val ssvf = ShutterStatusValue.format
        (json \ "value")
          .validate[ShutterStatusValue]
          .flatMap {
            case ShutterStatusValue.Unshuttered => JsSuccess(Unshuttered)
            case ShutterStatusValue.Shuttered =>
              JsSuccess(
                Shuttered(
                  reason = (json \ "reason").asOpt[String],
                  outageMessage = (json \ "outageMessage").asOpt[String],
                  useDefaultOutagePage = (json \ "useDefaultOutagePage").as[Boolean]
                )
              )
          }
      }

      override def writes(ss: ShutterStatus): JsValue = {
        implicit val ssvf = ShutterStatusValue.format
        ss match {
          case Shuttered(reason, outageMessage, useDefaultOutagePage) =>
            Json.obj(
              "value"                -> Json.toJson(ss.value),
              "reason"               -> reason,
              "outageMessage"        -> outageMessage,
              "useDefaultOutagePage" -> useDefaultOutagePage
            )
          case Unshuttered =>
            Json.obj(
              "value" -> Json.toJson(ss.value)
            )
        }
      }
    }
}

case class ShutterState(
  serviceName: ServiceName,
  context    : Option[String],
  shutterType: ShutterType,
  environment: Environment,
  status     : ShutterStatus
)

object ShutterState {

  val reads: Reads[ShutterState] = {
    implicit val stf = ShutterType.format
    implicit val ef  = ShutterEnvironment.format
    implicit val ssf = ShutterStatus.format
    ( (__ \ "name"       ).read[String].map(ServiceName.apply)
    ~ (__ \ "context"    ).readNullable[String]
    ~ (__ \ "type"       ).read[ShutterType]
    ~ (__ \ "environment").read[Environment]
    ~ (__ \ "status"     ).read[ShutterStatus]
    )(ShutterState.apply)
  }
}

// -------------- Events ---------------------

enum EventType(val asString: String):
  case ShutterStateCreate    extends EventType("shutter-state-create"   )
  case ShutterStateDelete    extends EventType("shutter-state-delete"   )
  case ShutterStateChange    extends EventType("shutter-state-change"   )
  case KillSwitchStateChange extends EventType("killswitch-state-change")

object EventType {
  def parse(s: String): Option[EventType] =
    values.find(_.asString == s)

  val format: Format[EventType] = new Format[EventType] {
    override def reads(json: JsValue) =
      json
        .validate[String]
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

enum ShutterCause(val asString: String):
  case Scheduled      extends ShutterCause("scheduled"      )
  case UserCreated    extends ShutterCause("user-shutter"   )
  case AutoReconciled extends ShutterCause("auto-reconciled")
  case Legacy         extends ShutterCause("legacy-shutter" )

object ShutterCause {
  def parse(s: String): Option[ShutterCause] =
    values.find(_.asString == s)

  val format: Format[ShutterCause] = new Format[ShutterCause] {
    override def reads(json: JsValue) =
      json
        .validate[String]
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

enum EventData:
  case ShutterStateCreateData(
    serviceName: String
  ) extends EventData

  case ShutterStateDeleteData(
    serviceName: String
  ) extends EventData

  case ShutterStateChangeData(
    serviceName: String,
    environment: Environment,
    shutterType: ShutterType,
    status     : ShutterStatus,
    cause      : ShutterCause
  ) extends EventData

  case KillSwitchStateChangeData(
    environment: Environment,
    status     : ShutterStatusValue
  ) extends EventData

object EventData {
  val shutterStateCreateDataFormat: Format[ShutterStateCreateData] =
    (__ \ "serviceName")
      .format[String]
      .inmap(ShutterStateCreateData.apply, _.serviceName)

  val shutterStateDeleteDataFormat: Format[ShutterStateDeleteData] =
    (__ \ "serviceName")
      .format[String]
      .inmap(ShutterStateDeleteData.apply, _.serviceName)

  val shutterStateChangeDataFormat: Format[ShutterStateChangeData] = {
    implicit val ef   = ShutterEnvironment.format
    implicit val st   = ShutterType.format
    implicit val ssvf = ShutterStatus.format
    implicit val scf  = ShutterCause.format

    ( (__ \ "serviceName").format[String]
    ~ (__ \ "environment").format[Environment]
    ~ (__ \ "shutterType").format[ShutterType]
    ~ (__ \ "status"     ).format[ShutterStatus]
    ~ (__ \ "cause"      ).format[ShutterCause]
    )(ShutterStateChangeData.apply, d => Tuple.fromProductTyped(d))
  }

  val killSwitchStateChangeDataFormat: Format[KillSwitchStateChangeData] = {
    implicit val ef   = ShutterEnvironment.format
    implicit val ssvf = ShutterStatusValue.format

    ( (__ \ "environment").format[Environment]
    ~ (__ \ "status"     ).format[ShutterStatusValue]
    )(KillSwitchStateChangeData.apply, d => Tuple.fromProductTyped(d))
  }

  def reads(et: EventType) =
    new Reads[EventData] {
      implicit val sscrdf: Reads[EventData.ShutterStateCreateData]    = shutterStateCreateDataFormat
      implicit val ssddf : Reads[EventData.ShutterStateDeleteData]    = shutterStateDeleteDataFormat
      implicit val sscdf : Reads[EventData.ShutterStateChangeData]    = shutterStateChangeDataFormat
      implicit val kscdf : Reads[EventData.KillSwitchStateChangeData] = killSwitchStateChangeDataFormat
      def reads(js: JsValue): JsResult[EventData] =
        et match {
          case EventType.ShutterStateCreate    => js.validate[EventData.ShutterStateCreateData]
          case EventType.ShutterStateDelete    => js.validate[EventData.ShutterStateDeleteData]
          case EventType.ShutterStateChange    => js.validate[EventData.ShutterStateChangeData]
          case EventType.KillSwitchStateChange => js.validate[EventData.KillSwitchStateChangeData]
        }
    }
}

case class ShutterEvent(
  username : String,
  timestamp: Instant,
  eventType: EventType,
  data     : EventData
) {
  def toShutterStateChangeEvent: Option[ShutterStateChangeEvent] =
    data match {
      case sscd: EventData.ShutterStateChangeData =>
        Some(
          ShutterStateChangeEvent(
            username    = username,
            timestamp   = timestamp,
            serviceName = sscd.serviceName,
            environment = sscd.environment,
            shutterType = sscd.shutterType,
            status      = sscd.status,
            cause       = sscd.cause
          )
        )
      case _ => None
    }
}

/** Special case flattened */
case class ShutterStateChangeEvent(
  username   : String,
  timestamp  : Instant,
  serviceName: String,
  environment: Environment,
  shutterType: ShutterType,
  status     : ShutterStatus,
  cause      : ShutterCause
)

object ShutterEvent {

  val reads: Reads[ShutterEvent] = {
    implicit val etf = EventType.format
    ( (__ \ "username" ).read[String]
    ~ (__ \ "timestamp").read[Instant]
    ~ (__ \ "type"     ).read[EventType]
    ~ (__ \ "type"     ).read[EventType]
                        .flatMap[EventData](et => (__ \ "data").read(EventData.reads(et)))
    )(ShutterEvent.apply)
  }
}

case class TemplatedContent(
  elementId: String,
  innerHtml: String
)

object TemplatedContent {
  val format: Format[TemplatedContent] =
    ( (__ \ "elementID").format[String]
    ~ (__ \ "innerHTML").format[String]
    )(TemplatedContent.apply, c => Tuple.fromProductTyped(c))
}

case class OutagePageWarning(
  name   : String,
  message: String
)

object OutagePageWarning {
  val reads: Reads[OutagePageWarning] =
    ( (__ \ "type"   ).read[String]
    ~ (__ \ "message").read[String]
    )(OutagePageWarning.apply)
}

case class OutagePage(
  serviceName      : ServiceName,
  environment      : Environment,
  outagePageURL    : String,
  warnings         : List[OutagePageWarning],
  templatedElements: List[TemplatedContent]
) {
  def templatedMessages: List[TemplatedContent] =
    templatedElements
      .filter(_.elementId == "templatedMessage")
}

object OutagePage {
  val reads: Reads[OutagePage] = {
    implicit val ef  : Reads[Environment]       = ShutterEnvironment.format
    implicit val tcf : Reads[TemplatedContent]  = TemplatedContent.format
    implicit val opwr: Reads[OutagePageWarning] = OutagePageWarning.reads
    ( (__ \ "serviceName"      ).read[String].map(ServiceName.apply)
    ~ (__ \ "environment"      ).read[Environment]
    ~ (__ \ "outagePageURL"    ).read[String]
    ~ (__ \ "warnings"         ).read[List[OutagePageWarning]]
    ~ (__ \ "templatedElements").read[List[TemplatedContent]]
    )(OutagePage.apply)
  }
}

case class OutagePageStatus(
  serviceName: ServiceName,
  warning    : Option[(String, String)]
)

case class FrontendRouteWarning(
  name                : String,
  message             : String,
  consequence         : String,
  ruleConfigurationURL: String
)

object FrontendRouteWarning {
  val reads: Reads[FrontendRouteWarning] =
    ( (__ \ "name"                ).read[String]
    ~ (__ \ "message"             ).read[String]
    ~ (__ \ "consequence"         ).read[String]
    ~ (__ \ "ruleConfigurationURL").read[String]
    )(FrontendRouteWarning.apply)
}
