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

import org.apache.http.client.utils.URIBuilder
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.connector.model.{TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.model.Environment

import java.net.URI
import java.time.Instant

sealed trait Platform {
  def asString: String
  def displayName: String
}

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

object JsonCodecs {
  def format[A, B](f: A => B, g: B => A)(implicit fa: Format[A]): Format[B] = fa.inmap(f, g)

  private def toResult[A](e: Either[String, A]): JsResult[A] =
    e match {
      case Right(r) => JsSuccess(r)
      case Left(l)  => JsError(__, l)
    }

  val serviceNameFormat     : Format[ServiceName]      = format(ServiceName.apply     , _.asString)
  val timeSeenFormat        : Format[TimeSeen]         = format(TimeSeen.apply        , _.time    )
  val teamNameFormat        : Format[TeamName]         = format(TeamName.apply        , _.asString)
  val usernameReads         : Format[Username]         = format(Username.apply        , _.asString)
  val deploymentStatusFormat: Format[DeploymentStatus] = format(DeploymentStatus.apply, _.asString)

  val environmentFormat: Format[Environment] = new Format[Environment] {
    override def reads(json: JsValue): JsResult[Environment] =
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

  val whatsRunningWhereVersionReads: Reads[WhatsRunningWhereVersion] = {
    implicit val wf  = environmentFormat
    implicit val vnf = Version.format
    implicit val cnf: Reads[WhatsRunningWhereConfig] =
      ( (__ \ "repoName").read[String]
      ~ (__ \ "fileName").read[String]
      ~ (__ \ "commitId").read[String]
      )(WhatsRunningWhereConfig.apply _)

    ( (__ \ "environment"  ).read[Environment]
    ~ (__ \ "versionNumber").read[Version]
    ~ (__ \ "config"       ).read[List[WhatsRunningWhereConfig]]
    )(WhatsRunningWhereVersion.apply _)
  }

  val whatsRunningWhereReads: Reads[WhatsRunningWhere] = {
    implicit val wf    = serviceNameFormat
    implicit val wrwvf = whatsRunningWhereVersionReads
    ( (__ \ "applicationName").read[ServiceName]
    ~ (__ \ "versions"       ).read[List[WhatsRunningWhereVersion]]
    )(WhatsRunningWhere.apply _)
  }

  val profileTypeFormat: Format[ProfileType] = new Format[ProfileType] {
    override def reads(js: JsValue): JsResult[ProfileType] =
      js.validate[String]
        .flatMap(s => toResult(ProfileType.parse(s)))

    override def writes(et: ProfileType): JsValue =
      JsString(et.asString)
  }

  val profileNameFormat: Format[ProfileName] =
    format(ProfileName.apply, _.asString)

  val profileFormat: OFormat[Profile] = {
    implicit val ptf = profileTypeFormat
    implicit val pnf = profileNameFormat
    ( (__ \ "type").format[ProfileType]
    ~ (__ \ "name").format[ProfileName]
    )(Profile.apply, p => Tuple.fromProductTyped(p))
  }

  // Deployment Event
  val deploymentEventFormat: Format[DeploymentEvent] = {
    implicit val vnf = Version.format
    implicit val tsf = timeSeenFormat
    implicit val dsf = deploymentStatusFormat
    ( (__ \ "deploymentId").format[String]
    ~ (__ \ "status"      ).format[DeploymentStatus]
    ~ (__ \ "version"     ).format[Version]
    ~ (__ \ "time"        ).format[TimeSeen]
    )(DeploymentEvent.apply, de => Tuple.fromProductTyped(de))
  }

  val serviceDeploymentsFormat: Format[ServiceDeployment] = {
    implicit val devf = deploymentEventFormat
    implicit val snf  = serviceNameFormat
    implicit val enf  = environmentFormat
    ( (__ \ "serviceName"     ).format[ServiceName]
    ~ (__ \ "environment"     ).format[Environment]
    ~ (__ \ "deploymentEvents").format[Seq[DeploymentEvent]]
    ~ (__ \ "lastCompleted"   ).formatNullable[DeploymentEvent]
    )(ServiceDeployment.apply, sd => Tuple.fromProductTyped(sd))
  }

  val deploymentHistoryReads: Reads[DeploymentHistory] = {
    implicit val snf = serviceNameFormat
    implicit val ef  = environmentFormat
    implicit val vnf = Version.format
    implicit val tsf = timeSeenFormat
    implicit val dar = usernameReads
    implicit val tmf = teamNameFormat
    ( (__ \ "serviceName").read[ServiceName]
    ~ (__ \ "environment").read[Environment]
    ~ (__ \ "version"    ).read[Version]
    ~ (__ \ "teams"      ).read[Seq[TeamName]]
    ~ (__ \ "time"       ).read[TimeSeen]
    ~ (__ \ "username"   ).read[Username]
    )(DeploymentHistory.apply _)
  }

  val deploymentTimelineEventReads: Reads[DeploymentTimelineEvent] = {
    implicit val ef  = environmentFormat
    ( (__ \ "environment"  ).read[Environment]
    ~ (__ \ "version"      ).read[Version](Version.format)
    ~ (__ \ "deploymentId" ).read[String]
    ~ (__ \ "username"     ).read[String]
    ~ (__ \ "start"        ).read[Instant]
    ~ (__ \ "end"          ).read[Instant]
    ~ (__ \ "displayStart" ).readNullable[Instant]
    ~ (__ \ "displayEnd"   ).readNullable[Instant]
    ~ (__ \ "configChanged").readNullable[Boolean]
    ~ (__ \ "configId"     ).readNullable[String]
    )(DeploymentTimelineEvent.apply _ )
  }
}

case class TimeSeen(time: Instant)

object TimeSeen {
  implicit val timeSeenOrdering: Ordering[TimeSeen] = {
    implicit val io: Ordering[Instant] = Ordering.by(_.toEpochMilli)
    Ordering.by(_.time)
  }
}

case class ServiceName(asString: String) extends AnyVal
object ServiceName {
  implicit val serviceNameOrdering: Ordering[ServiceName] =
    Ordering.by(_.asString)
}

case class ProfileName(asString: String) extends AnyVal

object ProfileName {
  implicit val ordering: Ordering[ProfileName] =
    Ordering.by(_.asString)
}

sealed trait ViewMode {
  def asString: String
}

object ViewMode {
  def parse(s: String): Either[String, ViewMode] =
    values
      .find(_.asString == s)
      .toRight(s"Invalid viewMode - should be one of ${values.map(_.asString).mkString(", ")}")

  val values: List[ViewMode] = List(
    Versions,
    Instances
  )

  case object Versions extends ViewMode {
    override val asString: String = "versions"
  }

  case object Instances extends ViewMode {
    override val asString: String = "instances"
  }
}

sealed trait ProfileType {
  def asString: String
}
object ProfileType {
  def parse(s: String): Either[String, ProfileType] =
    values
      .find(_.asString == s)
      .toRight(s"Invalid profileType - should be one of: ${values.map(_.asString).mkString(", ")}")

  val values: List[ProfileType] = List(
    Team,
    ServiceManager
  )
  case object Team extends ProfileType {
    override val asString: String = "team"
  }
  case object ServiceManager extends ProfileType {
    override val asString: String = "servicemanager"
  }
}

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
  username   : Username
)

case class Username(asString: String)

case class Pagination(
  page    : Int,
  pageSize: Int,
  total   : Long
)

object Pagination {
  def uriForPage(uri: String, page: Int): URI =
    new URIBuilder(uri)
      .setParameter("page", page.toString)
      .build()
}

case class DeploymentTimelineEvent(
  env          : Environment,
  version      : Version,
  deploymentId : String,
  userName     : String,
  start        : Instant,
  end          : Instant,
  displayStart : Option[Instant] = None, // set on the first/last event to the actual end date rather than the end of the chart
  displayEnd   : Option[Instant] = None,
  configChanged: Option[Boolean] = None,
  configId     : Option[String] = None,
)
