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

package uk.gov.hmrc.cataloguefrontend.serviceconfigs

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, Writes, __}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.util.{FromString, FromStringEnum, FormFormat, Parser}

import java.time.Instant

import FromStringEnum._

enum KeyFilterType(val asString: String) extends FromString derives Ordering, Writes:
  case Contains           extends KeyFilterType(asString = "contains"          )
  case ContainsIgnoreCase extends KeyFilterType(asString = "containsIgnoreCase")

object KeyFilterType extends FromStringEnum[KeyFilterType]:
  def toKeyFilterType(isIgnoreCase: Boolean): KeyFilterType =
    if (isIgnoreCase) ContainsIgnoreCase else Contains

given Parser[FormValueFilterType] = Parser.parser(FormValueFilterType.values)

enum FormValueFilterType(val asString: String, val displayString: String) extends FromString derives Ordering, Writes, FormFormat:
  case Contains       extends FormValueFilterType(asString = "contains"      , displayString = "Contains"        )
  case DoesNotContain extends FormValueFilterType(asString = "doesNotContain", displayString = "Does not contain")
  case EqualTo        extends FormValueFilterType(asString = "equalTo"       , displayString = "Equal to"        )
  case NotEqualTo     extends FormValueFilterType(asString = "notEqualTo"    , displayString = "Not Equal to"    )
  case IsEmpty        extends FormValueFilterType(asString = "isEmpty"       , displayString = "Is Empty"        )

object FormValueFilterType extends FromStringEnum[FormValueFilterType]


enum ValueFilterType(val asString: String) extends FromString derives Ordering, Writes:
  case Contains                 extends ValueFilterType("contains"                )
  case ContainsIgnoreCase       extends ValueFilterType("containsIgnoreCase"      )
  case DoesNotContain           extends ValueFilterType("doesNotContain"          )
  case DoesNotContainIgnoreCase extends ValueFilterType("doesNotContainIgnoreCase")
  case EqualTo                  extends ValueFilterType("equalTo"                 )
  case EqualToIgnoreCase        extends ValueFilterType("equalToIgnoreCase"       )
  case NotEqualTo               extends ValueFilterType("notEqualTo"              )
  case NotEqualToIgnoreCase     extends ValueFilterType("notEqualToIgnoreCase"    )
  case IsEmpty                  extends ValueFilterType("isEmpty"                 )

object ValueFilterType extends FromStringEnum[ValueFilterType]:
  // TODO move toValueFilterType(ignoreCase: Boolean) to FormValueFilterType
  def toValueFilterType(formValueFilterType: FormValueFilterType, isIgnoreCase: Boolean): ValueFilterType  =
    formValueFilterType match
      case FormValueFilterType.Contains       => if isIgnoreCase then ContainsIgnoreCase       else Contains
      case FormValueFilterType.DoesNotContain => if isIgnoreCase then DoesNotContainIgnoreCase else DoesNotContain
      case FormValueFilterType.EqualTo        => if isIgnoreCase then EqualToIgnoreCase        else EqualTo
      case FormValueFilterType.NotEqualTo     => if isIgnoreCase then NotEqualToIgnoreCase     else NotEqualTo
      case FormValueFilterType.IsEmpty        => IsEmpty

given Parser[GroupBy] = Parser.parser(GroupBy.values)

enum GroupBy(val asString: String, val displayString: String) extends FromString derives Ordering, Writes, FormFormat:
  case Key     extends GroupBy(asString = "key"    , displayString = "Key"    )
  case Service extends GroupBy(asString = "service", displayString = "Service")

object GroupBy extends FromStringEnum[GroupBy]

given Parser[ServiceType] = Parser.parser(ServiceType.values)

// TODO reuse connector.model.ServiceType
enum ServiceType(val asString: String, val displayString: String) extends FromString derives Ordering, Writes, FormFormat:
  case Frontend extends ServiceType(asString = "frontend", displayString = "Frontend")
  case Backend  extends ServiceType(asString = "backend" , displayString = "Backend" )

object ServiceType extends FromStringEnum[ServiceType]

case class DeploymentConfigEvent(
  serviceName  : ServiceName,
  environment  : Environment,
  deploymentId : String,
  configChanged: Option[Boolean],
  configId     : Option[String],
  lastUpdated  : Instant
)

object DeploymentConfigEvent {
  val reads: Reads[DeploymentConfigEvent] =
    ( (__ \ "serviceName"  ).read[ServiceName](ServiceName.format)
    ~ (__ \ "environment"  ).read[Environment]
    ~ (__ \ "deploymentId" ).read[String]
    ~ (__ \ "configChanged").readNullable[Boolean]
    ~ (__ \ "configId"     ).readNullable[String]
    ~ (__ \ "lastUpdated"  ).read[Instant]
    )(DeploymentConfigEvent.apply)
}
