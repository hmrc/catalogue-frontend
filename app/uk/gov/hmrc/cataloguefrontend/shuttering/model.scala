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

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.PathBindable
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, UserName}
import uk.gov.hmrc.cataloguefrontend.util.{FromString, FromStringEnum, Parser, FormFormat}

import java.time.Instant

import FromStringEnum._

given Parser[ShutterType] = Parser.parser(ShutterType.values)

enum ShutterType(val asString: String) extends FromString derives Ordering, Reads, Writes, PathBindable:
  case Frontend extends ShutterType("frontend")
  case Api      extends ShutterType("api"    )
  case Rate     extends ShutterType("rate"   )

object ShutterType extends FromStringEnum[ShutterType]

given Parser[ShutterStatusValue] = Parser.parser(ShutterStatusValue.values)

enum ShutterStatusValue(val asString: String) extends FromString derives Ordering, Reads, Writes, FormFormat:
  case Shuttered   extends ShutterStatusValue("shuttered"  )
  case Unshuttered extends ShutterStatusValue("unshuttered")

object ShutterStatusValue extends FromStringEnum[ShutterStatusValue]

enum ShutterStatus(val value: ShutterStatusValue):
  case Shuttered(
    reason              : Option[String],
    outageMessage       : Option[String],
    useDefaultOutagePage: Boolean
  ) extends ShutterStatus(ShutterStatusValue.Shuttered)
  case Unshuttered extends ShutterStatus(ShutterStatusValue.Unshuttered)

object ShutterStatus:
  val format: Format[ShutterStatus] =
    new Format[ShutterStatus]:
      override def reads(json: JsValue) =
        (json \ "value")
          .validate[ShutterStatusValue]
          .flatMap:
            case ShutterStatusValue.Unshuttered => JsSuccess(Unshuttered)
            case ShutterStatusValue.Shuttered =>
              JsSuccess:
                Shuttered(
                  reason               = (json \ "reason"              ).asOpt[String],
                  outageMessage        = (json \ "outageMessage"       ).asOpt[String],
                  useDefaultOutagePage = (json \ "useDefaultOutagePage").as[Boolean]
                )

      override def writes(ss: ShutterStatus): JsValue =
        ss match
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

case class ShutterState(
  serviceName: ServiceName,
  context    : Option[String],
  shutterType: ShutterType,
  environment: Environment,
  status     : ShutterStatus
)

object ShutterState {

  val reads: Reads[ShutterState] =
    ( (__ \ "name"       ).read[ServiceName](ServiceName.format)
    ~ (__ \ "context"    ).readNullable[String]
    ~ (__ \ "type"       ).read[ShutterType]
    ~ (__ \ "environment").read[Environment]
    ~ (__ \ "status"     ).read[ShutterStatus](ShutterStatus.format)
    )(ShutterState.apply)
}

// -------------- Events ---------------------

given Parser[EventType] = Parser.parser(EventType.values)

enum EventType(val asString: String) extends FromString derives Ordering, Reads:
  case ShutterStateCreate    extends EventType("shutter-state-create"   )
  case ShutterStateDelete    extends EventType("shutter-state-delete"   )
  case ShutterStateChange    extends EventType("shutter-state-change"   )
  case KillSwitchStateChange extends EventType("killswitch-state-change")

object EventType extends FromStringEnum[EventType]

given Parser[ShutterCause] = Parser.parser(ShutterCause.values)

enum ShutterCause(val asString: String) extends FromString derives Ordering, Reads:
  case Scheduled      extends ShutterCause("scheduled"      )
  case UserCreated    extends ShutterCause("user-shutter"   )
  case AutoReconciled extends ShutterCause("auto-reconciled")
  case Legacy         extends ShutterCause("legacy-shutter" )

object ShutterCause extends FromStringEnum[ShutterCause]

enum EventData:
  case ShutterStateCreateData(
    serviceName: ServiceName
  ) extends EventData

  case ShutterStateDeleteData(
    serviceName: ServiceName
  ) extends EventData

  case ShutterStateChangeData(
    serviceName: ServiceName,
    environment: Environment,
    shutterType: ShutterType,
    status     : ShutterStatus,
    cause      : ShutterCause
  ) extends EventData

  case KillSwitchStateChangeData(
    environment: Environment,
    status     : ShutterStatusValue
  ) extends EventData

object EventData:
  val shutterStateCreateDataReads: Reads[ShutterStateCreateData] =
    (__ \ "serviceName")
      .read[ServiceName](ServiceName.format)
      .map(ShutterStateCreateData.apply)

  val shutterStateDeleteDataReads: Reads[ShutterStateDeleteData] =
    (__ \ "serviceName")
      .read[ServiceName](ServiceName.format)
      .map(ShutterStateDeleteData.apply)

  val shutterStateChangeDataReads: Reads[ShutterStateChangeData] =
    ( (__ \ "serviceName").read[ServiceName  ](ServiceName.format  )
    ~ (__ \ "environment").read[Environment  ]
    ~ (__ \ "shutterType").read[ShutterType  ]
    ~ (__ \ "status"     ).read[ShutterStatus](ShutterStatus.format)
    ~ (__ \ "cause"      ).read[ShutterCause ]
    )((a, b, c, d, e) => ShutterStateChangeData(a, b, c, d, e))

  val killSwitchStateChangeDataReads: Reads[KillSwitchStateChangeData] =
    ( (__ \ "environment").read[Environment       ]
    ~ (__ \ "status"     ).read[ShutterStatusValue]
    )((a, b) => KillSwitchStateChangeData(a, b))

  def reads(et: EventType): Reads[EventData] =
    (js: JsValue) =>
      et match
        case EventType.ShutterStateCreate    => js.validate[ShutterStateCreateData](shutterStateCreateDataReads)
        case EventType.ShutterStateDelete    => js.validate[ShutterStateDeleteData](shutterStateDeleteDataReads)
        case EventType.ShutterStateChange    => js.validate[ShutterStateChangeData](shutterStateChangeDataReads)
        case EventType.KillSwitchStateChange => js.validate[KillSwitchStateChangeData](killSwitchStateChangeDataReads)
end EventData

case class ShutterEvent(
  username : UserName,
  timestamp: Instant,
  eventType: EventType,
  data     : EventData
):
  def toShutterStateChangeEvent: Option[ShutterStateChangeEvent] =
    data match
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

/** Special case flattened */
case class ShutterStateChangeEvent(
  username   : UserName,
  timestamp  : Instant,
  serviceName: ServiceName,
  environment: Environment,
  shutterType: ShutterType,
  status     : ShutterStatus,
  cause      : ShutterCause
)

object ShutterEvent:

  val reads: Reads[ShutterEvent] =
    ( (__ \ "username" ).read[UserName](UserName.format)
    ~ (__ \ "timestamp").read[Instant]
    ~ (__ \ "type"     ).read[EventType]
    ~ (__ \ "type"     ).read[EventType]
                        .flatMap[EventData](et => (__ \ "data").read(EventData.reads(et)))
    )(ShutterEvent.apply)

case class TemplatedContent(
  elementId: String,
  innerHtml: String
)

object TemplatedContent:
  val format: Format[TemplatedContent] =
    ( (__ \ "elementID").format[String]
    ~ (__ \ "innerHTML").format[String]
    )(TemplatedContent.apply, c => Tuple.fromProductTyped(c))

case class OutagePageWarning(
  name   : String,
  message: String
)

object OutagePageWarning:
  val reads: Reads[OutagePageWarning] =
    ( (__ \ "type"   ).read[String]
    ~ (__ \ "message").read[String]
    )(OutagePageWarning.apply)

case class OutagePage(
  serviceName      : ServiceName,
  environment      : Environment,
  outagePageURL    : String,
  warnings         : List[OutagePageWarning],
  templatedElements: List[TemplatedContent]
):
  def templatedMessages: List[TemplatedContent] =
    templatedElements
      .filter(_.elementId == "templatedMessage")

object OutagePage:
  val reads: Reads[OutagePage] =
    given Reads[TemplatedContent]  = TemplatedContent.format
    given Reads[OutagePageWarning] = OutagePageWarning.reads
    ( (__ \ "serviceName"      ).read[ServiceName](ServiceName.format)
    ~ (__ \ "environment"      ).read[Environment]
    ~ (__ \ "outagePageURL"    ).read[String]
    ~ (__ \ "warnings"         ).read[List[OutagePageWarning]]
    ~ (__ \ "templatedElements").read[List[TemplatedContent]]
    )(OutagePage.apply)

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

object FrontendRouteWarning:
  val reads: Reads[FrontendRouteWarning] =
    ( (__ \ "name"                ).read[String]
    ~ (__ \ "message"             ).read[String]
    ~ (__ \ "consequence"         ).read[String]
    ~ (__ \ "ruleConfigurationURL").read[String]
    )(FrontendRouteWarning.apply)
