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

package uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus

import cats.implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.util.{FormFormat, FromString, FromStringEnum, Parser}

import java.time.Instant

import FromStringEnum._

case class Warning(
  title: String
, message: String
)

object Warning:
  val reads: Reads[Warning] =
    ( (__ \ "title"  ).format[String]
    ~ (__ \ "message").format[String]
    )(Warning.apply, w => Tuple.fromProductTyped(w))

sealed trait Check:
  val id        : String = title.toLowerCase.replaceAll("\\s+", "-").replaceAll("-+", "-")
  val title     : String
  val helpText  : String
  val linkToDocs: Option[String]

object Check:
  case class Missing(addLink: String)
  case class Present(evidenceLink: String)

  type Result = Either[Missing, Present]

  sealed case class SimpleCheck(
    title       : String
  , checkResult : Result
  , helpText    : String
  , linkToDocs  : Option[String]
  ) extends Check

  sealed case class EnvCheck(
    title        : String
  , checkResults : Map[Environment, Result]
  , helpText: String
  , linkToDocs: Option[String]
  ) extends Check

  val reads: Reads[Check] =
    (checkJson: JsValue) =>
      given Reads[Result] =
        (json: JsValue) =>
          ((json \ "evidence").asOpt[String], (json \ "add").asOpt[String]) match
            case (Some(str), _) => JsSuccess(Right(Present(str)): Result)
            case (_, Some(str)) => JsSuccess(Left( Missing(str)): Result)
            case _              => JsError("Could not find either field 'evidence' or 'add'")

      given Reads[SimpleCheck] =
        ( (__ \ "title"      ).read[String]
        ~ (__ \ "simpleCheck").read[Result]
        ~ (__ \ "helpText"   ).read[String]
        ~ (__ \ "linkToDocs" ).readNullable[String]
        ) (SimpleCheck.apply)

      given Reads[Map[Environment, Result]] =
        import uk.gov.hmrc.cataloguefrontend.util.CategoryHelper.given cats.Applicative[JsResult]

        Reads
          .of[Map[JsString, Check.Result]]
          .flatMap: m =>
            _ =>
              m
                .toSeq
                .traverse: (k, v) =>
                  summon[Reads[Environment]].reads(k).map: e =>
                    (e, v)
                .map(_.toMap)

      given Reads[EnvCheck] =
        ( (__ \ "title"           ).read[String]
        ~ (__ \ "environmentCheck").read[Map[Environment, Result]]
        ~ (__ \ "helpText"        ).read[String]
        ~ (__ \ "linkToDocs"      ).readNullable[String]
        ) (EnvCheck.apply)

      checkJson
        .validate[SimpleCheck]
        .orElse(checkJson.validate[EnvCheck])

end Check

case class CachedServiceCheck(
  serviceName    : ServiceName
, lifecycleStatus: LifecycleStatus
, checks         : Seq[Check]
, warnings       : Option[Seq[Warning]]
)

object CachedServiceCheck:
  val reads: Reads[CachedServiceCheck] =
    given Reads[Warning] = Warning.reads
    given Reads[Check]   = Check.reads
    ( (__ \ "serviceName"    ).read[ServiceName](ServiceName.format)
    ~ (__ \ "lifecycleStatus").read[LifecycleStatus]
    ~ (__ \ "checks"         ).read[Seq[Check]]
    ~ (__ \ "warnings"       ).readNullable[Seq[Warning]]
    )(CachedServiceCheck.apply)

given Parser[FormCheckType] = Parser.parser(FormCheckType.values)

enum FormCheckType(
  override val asString: String
) extends FromString
  derives Ordering, Reads:
  case Simple      extends FormCheckType("simple"     )
  case Environment extends FormCheckType("environment")

case class Lifecycle(
  lifecycleStatus: LifecycleStatus
, username       : Option[String]  = None
, createDate     : Option[Instant] = None
)

object Lifecycle:
  val reads: Reads[Lifecycle] =
    ( (__ \ "lifecycleStatus").read[LifecycleStatus]
    ~ (__ \ "username"       ).readNullable[String]
    ~ (__ \ "createDate"     ).readNullable[Instant]
    )(Lifecycle.apply)

given Parser[LifecycleStatus] = Parser.parser(LifecycleStatus.values)

enum LifecycleStatus(
  override val asString: String,
  val displayName      : String
) extends FromString
  derives Reads, FormFormat:
  case Active                 extends LifecycleStatus(asString = "Active"                , displayName = "Active"         )
  case Archived               extends LifecycleStatus(asString = "Archived"              , displayName = "Archived"       )
  case DecommissionInProgress extends LifecycleStatus(asString = "DecommissionInProgress", displayName = "Decommissioning")
  case Deprecated             extends LifecycleStatus(asString = "Deprecated"            , displayName = "Deprecated"     )
  case Deleted                extends LifecycleStatus(asString = "Deleted"               , displayName = "Deleted"        )

object LifecycleStatus:
  def canDecommission(lifecycleStatus: LifecycleStatus): Boolean =
    List(Archived, DecommissionInProgress, Deleted).contains(lifecycleStatus)
