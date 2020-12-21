/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.Instant

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.connector.model.{TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.Platform.ECS

sealed trait Platform {
  def asString: String
  def displayName: String
}

object Platform {
  case object ECS extends Platform { override val asString      = "ecs"; override val displayName      = "Future Platform" }
  case object Heritage extends Platform { override val asString = "heritage"; override val displayName = "Heritage" }

  val values: List[Platform] =
    List(ECS, Heritage)

  def parse(s: String): Either[String, Platform] =
    values
      .find(_.asString equalsIgnoreCase s)
      .toRight(s"Invalid platform - should be one of: ${values.map(_.asString).mkString(", ")}")
}
case class WhatsRunningWhere(
  applicationName: ServiceName,
  versions: List[WhatsRunningWhereVersion]
)

case class WhatsRunningWhereVersion(
  environment: Environment,
  platform: Platform,
  versionNumber: VersionNumber,
  lastSeen: TimeSeen
)

object JsonCodecs {
  def format[A, B](f: A => B, g: B => A)(implicit fa: Format[A]): Format[B] = fa.inmap(f, g)

  private def toResult[A](e: Either[String, A]): JsResult[A] =
    e match {
      case Right(r) => JsSuccess(r)
      case Left(l)  => JsError(__, l)
    }

  val applicationNameFormat: Format[ServiceName] = format(ServiceName.apply, unlift(ServiceName.unapply))
  val versionNumberFormat: Format[VersionNumber] = format(VersionNumber.apply, unlift(VersionNumber.unapply))
  val timeSeenFormat: Format[TimeSeen]           = format(TimeSeen.apply, unlift(TimeSeen.unapply))
  val teamNameFormat: Format[TeamName]           = format(TeamName.apply, unlift(TeamName.unapply))
  val deploymentStatusFormat: Format[DeploymentStatus] =
    format(DeploymentStatus.apply, unlift(DeploymentStatus.unapply))

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
    implicit val pf  = platformFormat
    implicit val vnf = versionNumberFormat
    implicit val tsf = timeSeenFormat
    ((__ \ "environment").read[Environment]
      ~ (__ \ "platform").read[Platform]
      ~ (__ \ "versionNumber").read[VersionNumber]
      ~ (__ \ "lastSeen").read[TimeSeen])(WhatsRunningWhereVersion.apply _)
  }

  val whatsRunningWhereReads: Reads[WhatsRunningWhere] = {
    implicit val wf    = applicationNameFormat
    implicit val wrwvf = whatsRunningWhereVersionReads
    ((__ \ "applicationName").read[ServiceName]
      ~ (__ \ "versions").read[List[WhatsRunningWhereVersion]])(WhatsRunningWhere.apply _)
  }

  val profileTypeFormat: Format[ProfileType] = new Format[ProfileType] {
    override def reads(js: JsValue): JsResult[ProfileType] =
      js.validate[String]
        .flatMap(s => toResult(ProfileType.parse(s)))

    override def writes(et: ProfileType): JsValue =
      JsString(et.asString)
  }

  val profileNameFormat: Format[ProfileName] =
    format(ProfileName.apply, unlift(ProfileName.unapply))

  val profileFormat: OFormat[Profile] = {
    implicit val ptf = profileTypeFormat
    implicit val pnf = profileNameFormat
    ((__ \ "type").format[ProfileType]
      ~ (__ \ "name").format[ProfileName])(Profile.apply, unlift(Profile.unapply))
  }

  lazy val platformFormat: Format[Platform] = new Format[Platform] {
    override def reads(js: JsValue): JsResult[Platform] =
      js.validate[String]
        .flatMap(s => toResult(Platform.parse(s)))

    override def writes(platform: Platform): JsValue =
      JsString(platform.asString)
  }

  // ECS Deployment Event
  val deploymentEventFormat: Format[DeploymentEvent] = {
    implicit val vnf = versionNumberFormat
    implicit val tsf = timeSeenFormat
    implicit val dsf = deploymentStatusFormat
    ((__ \ "deploymentId").format[String]
      ~ (__ \ "status").format[DeploymentStatus]
      ~ (__ \ "version").format[VersionNumber]
      ~ (__ \ "time").format[TimeSeen])(DeploymentEvent.apply, unlift(DeploymentEvent.unapply))
  }

  val serviceDeploymentsFormat: Format[ServiceDeployment] = {
    implicit val devf = deploymentEventFormat
    implicit val anf  = applicationNameFormat
    implicit val enf  = environmentFormat
    ((__ \ "serviceName").format[ServiceName]
      ~ (__ \ "environment").format[Environment]
      ~ (__ \ "deploymentEvents").format[Seq[DeploymentEvent]]
      ~ (__ \ "lastCompleted").formatNullable[DeploymentEvent])(ServiceDeployment.apply, unlift(ServiceDeployment.unapply))
  }

  val deployerAuditReads: Reads[DeployerAudit] = {
    implicit val tsf = timeSeenFormat
    ((__ \ "userName").read[String]
      ~ (__ \ "deployTime").read[TimeSeen])(DeployerAudit.apply _)
  }

  val heritageDeploymentReads: Reads[Deployment] = {
    implicit val pf  = platformFormat
    implicit val anf = applicationNameFormat
    implicit val ef  = environmentFormat
    implicit val vnf = versionNumberFormat
    implicit val tsf = timeSeenFormat
    implicit val dar = deployerAuditReads
    implicit val tmf = teamNameFormat
    ((__ \ "platform").read[Platform]
      ~ (__ \ "name").read[ServiceName]
      ~ (__ \ "environment").read[Environment]
      ~ (__ \ "version").read[VersionNumber]
      ~ (__ \ "teams").read[Seq[TeamName]]
      ~ (__ \ "firstSeen").read[TimeSeen]
      ~ (__ \ "lastSeen").read[TimeSeen]
      ~ (__ \ "deployers").read[Seq[DeployerAudit]])(Deployment.apply _)
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

case class VersionNumber(asString: String) extends AnyVal {
  def asVersion: Version = Version(asString)
}

case class DeploymentStatus(asString: String) extends AnyVal

case class DeploymentEvent(
  deploymentId: String,
  status: DeploymentStatus,
  version: VersionNumber,
  time: TimeSeen
)

case class ServiceDeployment(
  serviceName: ServiceName,
  environment: Environment,
  deploymentEvents: Seq[DeploymentEvent],
  lastCompleted: Option[DeploymentEvent]
)

case class Deployment(
  platform: Platform,
  name: ServiceName,
  environment: Environment,
  version: VersionNumber,
  teams: Seq[TeamName],
  firstSeen: TimeSeen,
  lastSeen: TimeSeen,
  deployers: Seq[DeployerAudit] = Seq.empty) {

  lazy val isECS: Boolean = platform == ECS

  lazy val latestDeployer: Option[DeployerAudit] = {
    implicit val order = TimeSeen.timeSeenOrdering
    deployers.sortBy(_.deployTime).lastOption
  }

}

case class DeployerAudit(userName: String, deployTime: TimeSeen)
