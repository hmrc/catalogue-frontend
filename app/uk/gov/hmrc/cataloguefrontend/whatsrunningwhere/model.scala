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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, TeamName, UserName, Version}
import uk.gov.hmrc.cataloguefrontend.util.{FormFormat, FromString, Parser}

import java.net.URI
import java.time.Instant

case class WhatsRunningWhere(
  serviceName: ServiceName,
  versions   : List[WhatsRunningWhereVersion]
)

case class WhatsRunningWhereVersion(
  environment: Environment,
  version    : Version,
  config     : List[WhatsRunningWhereConfig]
)

case class WhatsRunningWhereConfig(
  repoName: String,
  filename: String,
  commitId: String
)

object JsonCodecs:
  def format[A, B](f: A => B, g: B => A)(using fa: Format[A]): Format[B] =
    fa.inmap(f, g)

  private def toResult[A](e: Either[String, A]): JsResult[A] =
    e match
      case Right(r) => JsSuccess(r)
      case Left(l)  => JsError(__, l)

  val timeSeenFormat        : Format[TimeSeen]         = format(TimeSeen.apply        , _.time    )
  val deploymentStatusFormat: Format[DeploymentStatus] = format(DeploymentStatus.apply, _.asString)

  val whatsRunningWhereVersionReads: Reads[WhatsRunningWhereVersion] =
    given Reads[WhatsRunningWhereConfig] =
      ( (__ \ "repoName").read[String]
      ~ (__ \ "fileName").read[String]
      ~ (__ \ "commitId").read[String]
      )(WhatsRunningWhereConfig.apply)

    ( (__ \ "environment"  ).read[Environment]
    ~ (__ \ "versionNumber").read[Version](Version.format)
    ~ (__ \ "config"       ).read[List[WhatsRunningWhereConfig]]
    )(WhatsRunningWhereVersion.apply)

  val whatsRunningWhereReads: Reads[WhatsRunningWhere] =
    given Reads[WhatsRunningWhereVersion] = whatsRunningWhereVersionReads
    ( (__ \ "applicationName").read[ServiceName]
    ~ (__ \ "versions"       ).read[List[WhatsRunningWhereVersion]]
    )(WhatsRunningWhere.apply)

  val profileTypeFormat: Format[ProfileType] =
    new Format[ProfileType]:
      override def reads(js: JsValue): JsResult[ProfileType] =
        js.validate[String]
          .flatMap(s => toResult(Parser[ProfileType].parse(s)))

      override def writes(et: ProfileType): JsValue =
        JsString(et.asString)

  val profileNameFormat: Format[ProfileName] =
    format(ProfileName.apply, _.asString)

  val profileFormat: Format[Profile] =
    ( (__ \ "type").format[ProfileType](profileTypeFormat)
    ~ (__ \ "name").format[ProfileName](profileNameFormat)
    )(Profile.apply, p => Tuple.fromProductTyped(p))

  // Deployment Event
  val deploymentEventFormat: Format[DeploymentEvent] =
    ( (__ \ "deploymentId").format[String]
    ~ (__ \ "status"      ).format[DeploymentStatus](deploymentStatusFormat)
    ~ (__ \ "version"     ).format[Version](Version.format)
    ~ (__ \ "time"        ).format[TimeSeen](timeSeenFormat)
    )(DeploymentEvent.apply, de => Tuple.fromProductTyped(de))

  val serviceDeploymentsFormat: Format[ServiceDeployment] =
    given Format[DeploymentEvent] = deploymentEventFormat
    ( (__ \ "serviceName"     ).format[ServiceName]
    ~ (__ \ "environment"     ).format[Environment]
    ~ (__ \ "deploymentEvents").format[Seq[DeploymentEvent]]
    ~ (__ \ "lastCompleted"   ).formatNullable[DeploymentEvent]
    )(ServiceDeployment.apply, sd => Tuple.fromProductTyped(sd))

  val deploymentHistoryReads: Reads[DeploymentHistory] =
    ( (__ \ "serviceName").read[ServiceName]
    ~ (__ \ "environment").read[Environment]
    ~ (__ \ "version"    ).read[Version](Version.format)
    ~ (__ \ "teams"      ).read[Seq[TeamName]]
    ~ (__ \ "time"       ).read[TimeSeen](timeSeenFormat)
    ~ (__ \ "username"   ).read[UserName]
    )(DeploymentHistory.apply)

  val deploymentTimelineEventReads: Reads[DeploymentTimelineEvent] =
    ( (__ \ "environment"            ).read[Environment]
    ~ (__ \ "version"                ).read[Version](Version.format)
    ~ (__ \ "deploymentId"           ).read[String]
    ~ (__ \ "username"               ).read[String]
    ~ (__ \ "start"                  ).read[Instant]
    ~ (__ \ "end"                    ).read[Instant]
    ~ (__ \ "displayStart"           ).readNullable[Instant]
    ~ (__ \ "displayEnd"             ).readNullable[Instant]
    ~ (__ \ "configChanged"          ).readNullable[Boolean]
    ~ (__ \ "deploymentConfigChanged").readNullable[Boolean]
    ~ (__ \ "configId"               ).readNullable[String]
    )(DeploymentTimelineEvent.apply)
end JsonCodecs


case class TimeSeen(time: Instant)

object TimeSeen:
  given Ordering[TimeSeen] =
    Ordering.by(_.time.toEpochMilli)

case class ProfileName(asString: String) extends AnyVal

object ProfileName:
  given Ordering[ProfileName] =
    Ordering.by(_.asString.toLowerCase)

given Parser[ViewMode] = Parser.parser(ViewMode.values)

enum ViewMode(
  override val asString: String
) extends FromString
  derives FormFormat:
  case Versions  extends ViewMode("versions")
  case Instances extends ViewMode("instances")


given Parser[ProfileType] = Parser.parser(ProfileType.values)

enum ProfileType(
  override val asString: String
) extends FromString
  derives FormFormat:
  case Team           extends ProfileType("team")
  case ServiceManager extends ProfileType("servicemanager")


case class Profile(
  profileType: ProfileType,
  profileName: ProfileName
)

case class DeploymentStatus(asString: String) extends AnyVal

case class DeploymentEvent(
  deploymentId: String,
  status      : DeploymentStatus,
  version     : Version,
  time        : TimeSeen
)

case class ServiceDeployment(
  serviceName     : ServiceName,
  environment     : Environment,
  deploymentEvents: Seq[DeploymentEvent],
  lastCompleted   : Option[DeploymentEvent]
)

case class PaginatedDeploymentHistory(
  history: Seq[DeploymentHistory],
  total  : Long
)

case class DeploymentHistory(
  name       : ServiceName,
  environment: Environment,
  version    : Version,
  teams      : Seq[TeamName],
  time       : TimeSeen,
  username   : UserName
)

case class Pagination(
  page    : Int,
  pageSize: Int,
  total   : Long
)

object Pagination:
  def uriForPage(uri: String, page: Int): URI =
    import sttp.model.Uri.UriContext
    val u = uri"$uri"
    u.withParams(u.paramsMap ++ Map("page" -> page.toString)).toJavaUri // ensures existing page param is replaced



case class DeploymentTimelineEvent(
  env                    : Environment,
  version                : Version,
  deploymentId           : String,
  userName               : String,
  start                  : Instant,
  end                    : Instant,
  displayStart           : Option[Instant] = None, // set on the first/last event to the actual end date rather than the end of the chart
  displayEnd             : Option[Instant] = None,
  configChanged          : Option[Boolean] = None,
  deploymentConfigChanged: Option[Boolean] = None,
  configId               : Option[String]  = None
)
