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

package uk.gov.hmrc.cataloguefrontend.connector

import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.config.Constant
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.Zone
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

sealed trait RepoType { def asString: String }

object RepoType {
  case object Service   extends RepoType { override val asString = "Service"   }
  case object Library   extends RepoType { override val asString = "Library"   }
  case object Prototype extends RepoType { override val asString = "Prototype" }
  case object Test      extends RepoType { override val asString = "Test"      }
  case object Other     extends RepoType { override val asString = "Other"     }

  val values: List[RepoType] = List(Service, Library, Prototype, Test, Other)

  def parse(s: String): Either[String, RepoType] =
    values
      .find(_.asString == s)
      .toRight(s"Invalid repoType - should be one of: ${values.map(_.asString).mkString(", ")}")

  val format: Format[RepoType] =
    new Format[RepoType] {
      override def reads(json: JsValue): JsResult[RepoType] =
        json match {
          case JsString(s) => parse(s).fold(msg => JsError(msg), rt => JsSuccess(rt))
          case _           => JsError("String value expected")
        }

      override def writes(rt: RepoType): JsValue =
        JsString(rt.asString)
    }
}

sealed trait ServiceType {
  def asString: String
  val displayString: String
}

object ServiceType {
  case object Frontend extends ServiceType {
    override val asString      = "frontend"
    override val displayString = "Service (Frontend)"
  }

  case object Backend extends ServiceType {
    override val asString = "backend"
    override val displayString = "Service (Backend)"
  }

  val values =
    Set(
      Frontend,
      Backend
    )

  def displayString = s"Service ($ServiceType)"

  def parse(s: String): Either[String, ServiceType] =
    values
      .find(_.asString.equalsIgnoreCase(s))
      .toRight(s"Invalid serviceType - should be one of: ${values.map(_.asString).mkString(", ")}")

  def apply(value: String): Option[ServiceType] = values.find(_.asString == value)

  val stFormat: Format[ServiceType] = new Format[ServiceType] {
    override def reads(json: JsValue): JsResult[ServiceType] =
      json.validate[String].flatMap { str =>
        ServiceType(str).fold[JsResult[ServiceType]](JsError(s"Invalid Service Type: $str"))(JsSuccess(_))
      }

    override def writes(o: ServiceType): JsValue = JsString(o.asString)
  }
}

sealed trait Tag {
  def asString: String
  val displayString: String
}

object Tag {
  case object AdminFrontend extends Tag {
    def asString = "admin";
    val displayString = "Admin Frontend"
  }

  case object Api extends Tag {
    def asString = "api";
    val displayString = "API"
  }

  case object Stub extends Tag {
    def asString = "stub";
    val displayString = "Stub"
  }

  val values =
    Set(AdminFrontend, Api, Stub)

  def parse(s: String): Either[String, Tag] =
    values
      .find(_.asString.equalsIgnoreCase(s))
      .toRight(s"Invalid tag - should be one of: ${values.map(_.asString).mkString(", ")}")

  val format: Format[Tag] = new Format[Tag] {
    override def reads(json: JsValue): JsResult[Tag] =
      json.validate[String].flatMap(s => parse(s).fold(msg => JsError(msg), t => JsSuccess(t)))

    override def writes(o: Tag): JsValue = JsString(o.asString)
  }
}

case class Link(
  name       : String,
  displayName: String,
  url        : String,
  cls        : Option[String] = None
) {
  val id: String =
    displayName.toLowerCase.replaceAll(" ", "-")
}

object Link {
  val format: Format[Link] =
    Json.format[Link]
}

case class BuildData(
  number     : Int,
  url        : String,
  timestamp  : Instant,
  result     : Option[String],
  description: Option[String]
)

object BuildData {
  val apiFormat: OFormat[BuildData] =
    ( (__ \ "number"     ).format[Int]
    ~ (__ \ "url"        ).format[String]
    ~ (__ \ "timestamp"  ).format[Instant]
    ~ (__ \ "result"     ).formatNullable[String]
    ~ (__ \ "description").formatNullable[String]
  )(apply, unlift(unapply))
}

sealed trait BuildJobType { def asString: String }

object BuildJobType {
  case object Job         extends BuildJobType { override val asString = "job"          }
  case object Pipeline    extends BuildJobType { override val asString = "pipeline"     }
  case object PullRequest extends BuildJobType { override val asString = "pull-request" }

  private val logger = Logger(this.getClass)

  val values: List[BuildJobType] =
    List(Job, Pipeline, PullRequest)

  implicit val ordering: Ordering[BuildJobType] =
    new Ordering[BuildJobType] {
      def compare(x: BuildJobType, y: BuildJobType): Int =
        values.indexOf(x).compare(values.indexOf(y))
    }

  def parse(s: String): BuildJobType =
    values
      .find(_.asString.equalsIgnoreCase(s)).getOrElse {
      logger.info(s"Unable to find job type: $s, defaulted to: job")
      Job
    }

  implicit val format: Format[BuildJobType] =
    Format.of[String].inmap(parse, _.asString)
}

case class JenkinsJob(
  name       : String,
  jenkinsURL : String,
  jobType    : BuildJobType,
  latestBuild: Option[BuildData]
)

object JenkinsJob {
  implicit val bdf: OFormat[BuildData] = BuildData.apiFormat
  val apiFormat: OFormat[JenkinsJob] =
    ( (__ \ "jobName"    ).format[String]
    ~ (__ \ "jenkinsURL" ).format[String]
    ~ (__ \ "jobType"    ).format[BuildJobType]
    ~ (__ \ "latestBuild").formatNullable[BuildData]
  )(apply, unlift(unapply))
}

case class GitRepository(
  name                : String,
  description         : String,
  githubUrl           : String,
  createdDate         : Instant,
  lastActiveDate      : Instant,
  isPrivate           : Boolean                  = false,
  repoType            : RepoType                 = RepoType.Other,
  serviceType         : Option[ServiceType]      = None,
  tags                : Option[Set[Tag]]         = None,
  digitalServiceName  : Option[String]           = None,
  owningTeams         : Seq[TeamName]            = Seq.empty,
  language            : Option[String],
  isArchived          : Boolean,
  defaultBranch       : String,
  branchProtection    : Option[BranchProtection] = None,
  isDeprecated        : Boolean                  = false,
  teamNames           : Seq[TeamName]            = Seq.empty,
  jenkinsJobs         : Seq[JenkinsJob]          = Seq.empty,
  prototypeName       : Option[String]           = None,
  prototypeAutoPublish: Option[Boolean]          = None,
  zone                : Option[Zone]             = None,
) {
  def isShared: Boolean =
    teamNames.length >= Constant.sharedRepoTeamsCutOff

  def isDeFactoOwner(teamName: TeamName): Boolean =
    owningTeams.contains(teamName) && owningTeams.toSet != teamNames.toSet

  val descriptionAboveLimit: Boolean = description.length > 512
}

object GitRepository {
  val apiFormat: OFormat[GitRepository] = {
    implicit val rtF  : Format[RepoType]    = RepoType.format
    implicit val stF  : Format[ServiceType] = ServiceType.stFormat
    implicit val jjF  : Format[JenkinsJob]  = JenkinsJob.apiFormat
    implicit val tagF : Format[Tag]         = Tag.format
    implicit val zoneF: Format[Zone]        = Zone.format
    implicit val tnf  : Format[TeamName]    = TeamName.format

    ( (__ \ "name"                ).format[String]
    ~ (__ \ "description"         ).format[String]
    ~ (__ \ "url"                 ).format[String]
    ~ (__ \ "createdDate"         ).format[Instant]
    ~ (__ \ "lastActiveDate"      ).format[Instant]
    ~ (__ \ "isPrivate"           ).formatWithDefault[Boolean](false)
    ~ (__ \ "repoType"            ).format[RepoType]
    ~ (__ \ "serviceType"         ).formatNullable[ServiceType]
    ~ (__ \ "tags"                ).formatNullable[Set[Tag]]
    ~ (__ \ "digitalServiceName"  ).formatNullable[String]
    ~ (__ \ "owningTeams"         ).formatWithDefault[Seq[TeamName]](Seq.empty)
    ~ (__ \ "language"            ).formatNullable[String]
    ~ (__ \ "isArchived"          ).formatWithDefault[Boolean](false)
    ~ (__ \ "defaultBranch"       ).format[String]
    ~ (__ \ "branchProtection"    ).formatNullable(BranchProtection.format)
    ~ (__ \ "isDeprecated"        ).formatWithDefault[Boolean](false)
    ~ (__ \ "teamNames"           ).formatWithDefault[Seq[TeamName]](Seq.empty)
    ~ (__ \ "jenkinsJobs"         ).formatWithDefault[Seq[JenkinsJob]](Seq.empty[JenkinsJob])
    ~ (__ \ "prototypeName"       ).formatNullable[String]
    ~ (__ \ "prototypeAutoPublish").formatNullable[Boolean]
    ~ (__ \ "zone"                ).formatNullable[Zone]
    ) (apply, unlift(unapply))
  }
}

final case class BranchProtection(
  requiresApprovingReviews: Boolean,
  dismissesStaleReviews   : Boolean,
  requiresCommitSignatures: Boolean
) {

  def isProtected: Boolean =
    requiresApprovingReviews && dismissesStaleReviews && requiresCommitSignatures
}

object BranchProtection {

  val format: Format[BranchProtection] =
    ( (__ \ "requiresApprovingReviews").format[Boolean]
    ~ (__ \ "dismissesStaleReviews"   ).format[Boolean]
    ~ (__ \ "requiresCommitSignatures").format[Boolean]
    )(apply, unlift(unapply))
}

case class Team(
  name           : TeamName,
  lastActiveDate : Option[Instant],
  repos          : Seq[String]
)

object Team {
  val format: OFormat[Team] = {
    implicit val tnf = TeamName.format
    ( (__ \ "name"          ).format[TeamName]
    ~ (__ \ "lastActiveDate").formatNullable[Instant]
    ~ (__ \ "repos"         ).format[Seq[String]]
    )(apply, unlift(unapply))
  }
}

@Singleton
class TeamsAndRepositoriesConnector @Inject()(
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._
  import TeamsAndRepositoriesConnector._

  private val logger = Logger(getClass)

  private val teamsAndServicesBaseUrl: String =
    servicesConfig.baseUrl("teams-and-repositories")

  private implicit val tf  : Format[Team]          = Team.format
  private implicit val ghrf: Format[GitRepository] = GitRepository.apiFormat // v2 model

  def lookupLatestJenkinsJobs(service: String)(implicit hc: HeaderCarrier): Future[Seq[JenkinsJob]] = {

    implicit val dr: Reads[Seq[JenkinsJob]] =
      Reads.at(__ \ "jobs")(Reads.seq(JenkinsJob.apiFormat))

    val url = url"$teamsAndServicesBaseUrl/api/v2/repositories/$service/jenkins-jobs"
    httpClientV2
      .get(url)
      .execute[Seq[JenkinsJob]]
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty[JenkinsJob]
      }
  }

  def findRelatedTestRepos(service: String)(implicit hc: HeaderCarrier): Future[Seq[String]] = {
    val url = url"$teamsAndServicesBaseUrl/api/v2/repositories/$service/test-repositories"

    httpClientV2
      .get(url)
      .execute[Seq[String]]
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty[String]
      }
  }

  def findServicesUnderTest(testRepo: String)(implicit hc: HeaderCarrier): Future[Seq[String]] = {
    val url = url"$teamsAndServicesBaseUrl/api/v2/repositories/$testRepo/services-under-test"

    httpClientV2
      .get(url)
      .execute[Seq[String]]
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty[String]
      }
  }

  def allTeams()(implicit hc: HeaderCarrier): Future[Seq[Team]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/v2/teams")
      .execute[Seq[Team]]

  def repositoriesForTeam(teamName: TeamName, includeArchived: Option[Boolean] = None)(implicit hc: HeaderCarrier): Future[Seq[GitRepository]] = {
    val url = url"$teamsAndServicesBaseUrl/api/v2/repositories?owningTeam=${teamName.asString}&archived=$includeArchived"

    httpClientV2
      .get(url)
      .execute[Seq[GitRepository]]
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Nil
      }
  }

  def allRepositories(
     name       : Option[String]      = None,
     team       : Option[TeamName]    = None,
     archived   : Option[Boolean]     = None,
     repoType   : Option[RepoType]    = None,
     serviceType: Option[ServiceType] = None
   )(implicit hc: HeaderCarrier): Future[Seq[GitRepository]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/v2/repositories?name=$name&owningTeam=${team.map(_.asString)}&archived=$archived&repoType=${repoType.map(_.asString)}&serviceType=${serviceType.map(_.asString)}")
      .execute[Seq[GitRepository]]

  def allServices()(implicit hc: HeaderCarrier): Future[Seq[GitRepository]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/v2/repositories?repoType=Service")
      .execute[Seq[GitRepository]]

  def archivedRepositories()(implicit hc: HeaderCarrier): Future[Seq[GitRepository]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/v2/repositories?archived=true")
      .execute[Seq[GitRepository]]

  def repositoryDetails(name: String)(implicit hc: HeaderCarrier): Future[Option[GitRepository]] =
    for {
      repo     <- httpClientV2
                    .get(url"$teamsAndServicesBaseUrl/api/v2/repositories/$name")
                    .execute[Option[GitRepository]]
      jobs     <- lookupLatestJenkinsJobs(name)
      withJobs =  repo.map(_.copy(jenkinsJobs = jobs))
    } yield withJobs

  def allTeamsByService()(implicit hc: HeaderCarrier): Future[Map[ServiceName, Seq[TeamName]]] =
    for {
      repos          <- httpClientV2
                          .get(url"$teamsAndServicesBaseUrl/api/v2/repositories")
                          .execute[Seq[GitRepository]]
      teamsByService =  repos.map(r => r.name -> r.teamNames).toMap
    } yield teamsByService

  def enableBranchProtection(repoName: String)(implicit hc: HeaderCarrier): Future[Unit] =
    httpClientV2
      .post(url"$teamsAndServicesBaseUrl/api/v2/repositories/$repoName/branch-protection/enabled")
      .withBody(JsBoolean(true))
      .execute[Unit](HttpReads.Implicits.throwOnFailure(implicitly[HttpReads[Either[UpstreamErrorResponse, Unit]]]), implicitly[ExecutionContext])
}

object TeamsAndRepositoriesConnector {

  type ServiceName = String
}
