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

import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{Format, Json, __}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.util.{Enum, WithAsString}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

sealed trait KeyFilterType extends WithAsString

object KeyFilterType extends {

  case object Contains           extends KeyFilterType { val asString = "contains"}
  case object ContainsIgnoreCase extends KeyFilterType { val asString = "containsIgnoreCase"}

  def toKeyFilterType(isIgnoreCase: Boolean): KeyFilterType =
    if (isIgnoreCase) ContainsIgnoreCase else Contains
}

sealed trait FormValueFilterType extends WithAsString {val displayString: String;}

object FormValueFilterType extends Enum[FormValueFilterType] {

  case object Contains       extends FormValueFilterType { val asString = "contains";       val displayString = "Contains"         }
  case object DoesNotContain extends FormValueFilterType { val asString = "doesNotContain"; val displayString = "Does not contain" }
  case object EqualTo        extends FormValueFilterType { val asString = "equalTo";        val displayString = "Equal to"         }
  case object NotEqualTo     extends FormValueFilterType { val asString = "notEqualTo";     val displayString = "Not Equal to"     }
  case object IsEmpty        extends FormValueFilterType { val asString = "isEmpty";        val displayString = "Is Empty"         }

  override val values: List[FormValueFilterType] =
    List(Contains, DoesNotContain, EqualTo, NotEqualTo, IsEmpty)
}

sealed trait ValueFilterType extends WithAsString
object ValueFilterType {

  case object Contains                 extends ValueFilterType { val asString = "contains"                }
  case object ContainsIgnoreCase       extends ValueFilterType { val asString = "containsIgnoreCase"      }
  case object DoesNotContain           extends ValueFilterType { val asString = "doesNotContain"          }
  case object DoesNotContainIgnoreCase extends ValueFilterType { val asString = "doesNotContainIgnoreCase"}
  case object EqualTo                  extends ValueFilterType { val asString = "equalTo"                 }
  case object EqualToIgnoreCase        extends ValueFilterType { val asString = "equalToIgnoreCase"       }
  case object NotEqualTo               extends ValueFilterType { val asString = "notEqualTo"              }
  case object NotEqualToIgnoreCase     extends ValueFilterType { val asString = "notEqualToIgnoreCase"    }
  case object IsEmpty                  extends ValueFilterType { val asString = "isEmpty"                 }

  def toValueFilterType(formValueFilterType: FormValueFilterType, isIgnoreCase: Boolean): ValueFilterType  =
    formValueFilterType match {
      case FormValueFilterType.Contains       => if (isIgnoreCase) ContainsIgnoreCase       else Contains
      case FormValueFilterType.DoesNotContain => if (isIgnoreCase) DoesNotContainIgnoreCase else DoesNotContain
      case FormValueFilterType.EqualTo        => if (isIgnoreCase) EqualToIgnoreCase        else EqualTo
      case FormValueFilterType.NotEqualTo     => if (isIgnoreCase) NotEqualToIgnoreCase     else NotEqualTo
      case FormValueFilterType.IsEmpty        => IsEmpty
    }
}

sealed trait GroupBy extends WithAsString {val displayString: String;}

object GroupBy extends Enum[GroupBy] {

  case object Key     extends GroupBy { val asString = "key";     val displayString = "Key"     }
  case object Service extends GroupBy { val asString = "service"; val displayString = "Service" }

  override val values: List[GroupBy] =
    List(Key, Service)
}

sealed trait ServiceType extends WithAsString {val displayString: String;}

object ServiceType extends Enum[ServiceType] {

  case object Frontend extends ServiceType { val asString = "frontend"; val displayString = "Frontend"}
  case object Backend  extends ServiceType { val asString = "backend";  val displayString = "Backend" }

  override val values: List[ServiceType] =
    List(Frontend, Backend)
}

case class DeploymentEventsRequest(serviceName: String, deploymentIds: Seq[String])

object DeploymentEventsRequest {
  implicit val format: Format[DeploymentEventsRequest] = Json.format[DeploymentEventsRequest]
}

case class DeploymentConfigEvent(
                            serviceName    : String,
                            environment    : Environment,
                            deploymentId   : String,
                            configChanged  : Boolean,
                            configId       : String,
                            lastUpdated    : Instant
                          )

object DeploymentConfigEvent {
  implicit val mongoFormats: Format[DeploymentConfigEvent] = {
    implicit val instantFormat = MongoJavatimeFormats.instantFormat
    implicit val ef = Environment.format
    ((__ \ "serviceName").format[String]
      ~ (__ \ "environment").format[Environment]
      ~ (__ \ "deploymentId").format[String]
      ~ (__ \ "configChanged").format[Boolean]
      ~ (__ \ "configId").format[String]
      ~ (__ \ "lastUpdated").format[Instant]
      )(DeploymentConfigEvent.apply, unlift(DeploymentConfigEvent.unapply))
  }
}
