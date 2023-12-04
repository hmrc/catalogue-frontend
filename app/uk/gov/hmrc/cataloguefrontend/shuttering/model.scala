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

sealed trait ShutterType { def asString: String }
object ShutterType {
  case object Frontend extends ShutterType { val asString = "frontend" }
  case object Api      extends ShutterType { val asString = "api" }
  case object Rate     extends ShutterType { val asString = "rate" }

  val values: List[ShutterType] = List(Frontend, Api, Rate)

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

sealed trait ShutterStatusValue { def asString: String }
object ShutterStatusValue {
  case object Shuttered   extends ShutterStatusValue { val asString = "shuttered" }
  case object Unshuttered extends ShutterStatusValue { val asString = "unshuttered" }

  val values = List(Shuttered, Unshuttered)

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

sealed trait ShutterStatus { def value: ShutterStatusValue }

object ShutterStatus {
  case class Shuttered(
    reason              : Option[String],
    outageMessage       : Option[String],
    useDefaultOutagePage: Boolean
  ) extends ShutterStatus { def value = ShutterStatusValue.Shuttered }
  case object Unshuttered extends ShutterStatus { def value = ShutterStatusValue.Unshuttered }

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
            case s => JsError(__, s"Invalid ShutterStatus '$s'")
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
  name       : String,
  shutterType: ShutterType,
  environment: Environment,
  status     : ShutterStatus
)

object ShutterState {

  val reads: Reads[ShutterState] = {
    implicit val stf = ShutterType.format
    implicit val ef  = ShutterEnvironment.format
    implicit val ssf = ShutterStatus.format
    ( (__ \ "name"       ).read[String]
    ~ (__ \ "type"       ).read[ShutterType]
    ~ (__ \ "environment").read[Environment]
    ~ (__ \ "status"     ).read[ShutterStatus]
    )(ShutterState.apply _)
  }
}

// -------------- Events ---------------------

sealed trait EventType { def asString: String }
object EventType {
  case object ShutterStateCreate    extends EventType { override val asString = "shutter-state-create" }
  case object ShutterStateDelete    extends EventType { override val asString = "shutter-state-delete" }
  case object ShutterStateChange    extends EventType { override val asString = "shutter-state-change" }
  case object KillSwitchStateChange extends EventType { override val asString = "killswitch-state-change" }

  val values = List(ShutterStateCreate, ShutterStateDelete, ShutterStateChange, KillSwitchStateChange)

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

sealed trait ShutterCause { def asString: String }
object ShutterCause {
  case object Scheduled      extends ShutterCause { override val asString = "scheduled" }
  case object UserCreated    extends ShutterCause { override val asString = "user-shutter" }
  case object AutoReconciled extends ShutterCause { override val asString = "auto-reconciled" }
  case object Legacy         extends ShutterCause { override val asString = "legacy-shutter" }

  val values = List(Scheduled, UserCreated, AutoReconciled, Legacy)

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

sealed trait EventData
object EventData {
  case class ShutterStateCreateData(
    serviceName: String
  ) extends EventData

  case class ShutterStateDeleteData(
    serviceName: String
  ) extends EventData

  case class ShutterStateChangeData(
    serviceName: String,
    environment: Environment,
    shutterType: ShutterType,
    status     : ShutterStatus,
    cause      : ShutterCause
  ) extends EventData

  case class KillSwitchStateChangeData(
    environment: Environment,
    status     : ShutterStatusValue
  ) extends EventData

  val shutterStateCreateDataFormat: Format[ShutterStateCreateData] =
    (__ \ "serviceName")
      .format[String]
      .inmap(ShutterStateCreateData.apply, unlift(ShutterStateCreateData.unapply))

  val shutterStateDeleteDataFormat: Format[ShutterStateDeleteData] =
    (__ \ "serviceName")
      .format[String]
      .inmap(ShutterStateDeleteData.apply, unlift(ShutterStateDeleteData.unapply))

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
    )(ShutterStateChangeData.apply, unlift(ShutterStateChangeData.unapply))
  }

  val killSwitchStateChangeDataFormat: Format[KillSwitchStateChangeData] = {
    implicit val ef   = ShutterEnvironment.format
    implicit val ssvf = ShutterStatusValue.format

    ( (__ \ "environment").format[Environment]
    ~ (__ \ "status"     ).format[ShutterStatusValue]
    )(KillSwitchStateChangeData.apply, unlift(KillSwitchStateChangeData.unapply))
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
    )(ShutterEvent.apply _)
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
    )(TemplatedContent.apply, unlift(TemplatedContent.unapply))
}

case class OutagePageWarning(
  name   : String,
  message: String
)

object OutagePageWarning {
  val reads: Reads[OutagePageWarning] =
    ( (__ \ "type"   ).read[String]
    ~ (__ \ "message").read[String]
    )(OutagePageWarning.apply _)
}

case class OutagePage(
  serviceName      : String,
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
    ( (__ \ "serviceName"      ).read[String]
    ~ (__ \ "environment"      ).read[Environment]
    ~ (__ \ "outagePageURL"    ).read[String]
    ~ (__ \ "warnings"         ).read[List[OutagePageWarning]]
    ~ (__ \ "templatedElements").read[List[TemplatedContent]]
    )(OutagePage.apply _)
  }
}

case class OutagePageStatus(
  serviceName: String,
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
    )(FrontendRouteWarning.apply _)
}
