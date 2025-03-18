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
import uk.gov.hmrc.cataloguefrontend.connector.ServiceType
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.util.{FormFormat, FromString, FromStringEnum, Parser}

import java.time.Instant

import FromStringEnum._

enum KeyFilterType(
  override val asString: String
) extends FromString
  derives Ordering, Writes:
  case Contains           extends KeyFilterType(asString = "contains"          )
  case ContainsIgnoreCase extends KeyFilterType(asString = "containsIgnoreCase")

object KeyFilterType:
  def apply(isIgnoreCase: Boolean): KeyFilterType =
    if isIgnoreCase then ContainsIgnoreCase else Contains

given Parser[FormValueFilterType] = Parser.parser(FormValueFilterType.values)

enum FormValueFilterType(
  override val asString: String,
  val displayString    : String
) extends FromString
  derives Ordering, Writes, FormFormat:
  case Contains       extends FormValueFilterType(asString = "contains"      , displayString = "Contains"        )
  case DoesNotContain extends FormValueFilterType(asString = "doesNotContain", displayString = "Does not contain")
  case EqualTo        extends FormValueFilterType(asString = "equalTo"       , displayString = "Equal to"        )
  case NotEqualTo     extends FormValueFilterType(asString = "notEqualTo"    , displayString = "Not Equal to"    )
  case IsEmpty        extends FormValueFilterType(asString = "isEmpty"       , displayString = "Is Empty"        )

enum ValueFilterType(
  override val asString: String
) extends FromString
  derives Ordering, Writes:
  case Contains                 extends ValueFilterType("contains"                )
  case ContainsIgnoreCase       extends ValueFilterType("containsIgnoreCase"      )
  case DoesNotContain           extends ValueFilterType("doesNotContain"          )
  case DoesNotContainIgnoreCase extends ValueFilterType("doesNotContainIgnoreCase")
  case EqualTo                  extends ValueFilterType("equalTo"                 )
  case EqualToIgnoreCase        extends ValueFilterType("equalToIgnoreCase"       )
  case NotEqualTo               extends ValueFilterType("notEqualTo"              )
  case NotEqualToIgnoreCase     extends ValueFilterType("notEqualToIgnoreCase"    )
  case IsEmpty                  extends ValueFilterType("isEmpty"                 )

object ValueFilterType:
  def apply(formValueFilterType: FormValueFilterType, isIgnoreCase: Boolean): ValueFilterType  =
    formValueFilterType match
      case FormValueFilterType.Contains       => if isIgnoreCase then ContainsIgnoreCase       else Contains
      case FormValueFilterType.DoesNotContain => if isIgnoreCase then DoesNotContainIgnoreCase else DoesNotContain
      case FormValueFilterType.EqualTo        => if isIgnoreCase then EqualToIgnoreCase        else EqualTo
      case FormValueFilterType.NotEqualTo     => if isIgnoreCase then NotEqualToIgnoreCase     else NotEqualTo
      case FormValueFilterType.IsEmpty        => IsEmpty

given Parser[GroupBy] = Parser.parser(GroupBy.values)

enum GroupBy(
  override val asString: String,
  val displayString    : String
) extends FromString
  derives Ordering, Writes, FormFormat:
  case Key     extends GroupBy(asString = "key"    , displayString = "Key"    )
  case Service extends GroupBy(asString = "service", displayString = "Service")

given Parser[ServiceType] = Parser.parser(ServiceType.values)

case class DeploymentConfigEvent(
  serviceName            : ServiceName,
  environment            : Environment,
  deploymentId           : String,
  configChanged          : Option[Boolean],
  deploymentConfigChanged: Option[Boolean],
  configId               : Option[String],
  lastUpdated            : Instant
)

object DeploymentConfigEvent {
  val reads: Reads[DeploymentConfigEvent] =
    ( (__ \ "serviceName"            ).read[ServiceName]
    ~ (__ \ "environment"            ).read[Environment]
    ~ (__ \ "deploymentId"           ).read[String]
    ~ (__ \ "configChanged"          ).readNullable[Boolean]
    ~ (__ \ "deploymentConfigChanged").readNullable[Boolean]
    ~ (__ \ "configId"               ).readNullable[String]
    ~ (__ \ "lastUpdated"            ).read[Instant]
    )(DeploymentConfigEvent.apply)
}

case class ServiceToRepoName(
  serviceName : String,
  artefactName: String,
  repoName    : String
)

object ServiceToRepoName:
  val reads: Reads[ServiceToRepoName] =
    ( (__ \ "serviceName" ).read[String]
    ~ (__ \ "artefactName").read[String]
    ~ (__ \ "repoName"    ).read[String]
    )(ServiceToRepoName.apply _)
