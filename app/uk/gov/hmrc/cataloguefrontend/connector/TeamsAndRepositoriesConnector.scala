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
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.cataloguefrontend.config.Constant
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.Zone
import uk.gov.hmrc.cataloguefrontend.util.{FromString, FromStringEnum}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

enum RepoType(val asString: String) extends FromString:
  case Service   extends RepoType("Service"  )
  case Library   extends RepoType("Library"  )
  case Prototype extends RepoType("Prototype")
  case Test      extends RepoType("Test"     )
  case Other     extends RepoType("Other"    )

object RepoType extends FromStringEnum[RepoType]

enum ServiceType(val asString: String, val displayString: String) extends FromString:
  case Frontend extends ServiceType(asString = "frontend", displayString = "Service (Frontend)")
  case Backend  extends ServiceType(asString = "backend" , displayString = "Service (Backend)")

object ServiceType extends FromStringEnum[ServiceType]

enum Tag(val asString: String, val displayString: String) extends FromString:
  case AdminFrontend    extends Tag(asString = "admin"             , displayString = "Admin Frontend"    )
  case Api              extends Tag(asString = "api"               , displayString = "API"               )
  case BuiltOffPlatform extends Tag(asString = "built-off-platform", displayString = "Built Off Platform")
  case Maven            extends Tag(asString = "maven"             , displayString = "Maven"             )
  case Stub             extends Tag(asString = "stub"              , displayString = "Stub"              )


object Tag extends FromStringEnum[Tag]

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
  )(apply, bd => Tuple.fromProductTyped(bd))
}

enum BuildJobType(val asString: String) extends FromString:
  case Job         extends BuildJobType("job"         )
  case Pipeline    extends BuildJobType("pipeline"    )
  case PullRequest extends BuildJobType("pull-request")

object BuildJobType extends FromStringEnum[BuildJobType]

case class JenkinsJob(
  name       : String,
  jenkinsURL : String,
  jobType    : BuildJobType,
  latestBuild: Option[BuildData]
)

object JenkinsJob {
  implicit val bdf: OFormat[BuildData]  = BuildData.apiFormat
  implicit val bjt: Format[BuildJobType] = BuildJobType.format
  val apiFormat: OFormat[JenkinsJob] =
    ( (__ \ "jobName"    ).format[String]
    ~ (__ \ "jenkinsURL" ).format[String]
    ~ (__ \ "jobType"    ).format[BuildJobType]
    ~ (__ \ "latestBuild").formatNullable[BuildData]
  )(apply, j => Tuple.fromProductTyped(j))
}

case class GitRepository(
  name                : String,
  description         : String,
  githubUrl           : String,
  createdDate         : Instant,
  lastActiveDate      : Instant,
  endOfLifeDate       : Option[Instant]          = None,
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
    implicit val stF  : Format[ServiceType] = ServiceType.format
    implicit val jjF  : Format[JenkinsJob]  = JenkinsJob.apiFormat
    implicit val tagF : Format[Tag]         = Tag.format
    implicit val zoneF: Format[Zone]        = Zone.format
    implicit val tnf  : Format[TeamName]    = TeamName.format

    ( (__ \ "name"                ).format[String]
    ~ (__ \ "description"         ).format[String]
    ~ (__ \ "url"                 ).format[String]
    ~ (__ \ "createdDate"         ).format[Instant]
    ~ (__ \ "lastActiveDate"      ).format[Instant]
    ~ (__ \ "endOfLifeDate"       ).formatNullable[Instant]
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
    ) (apply, r => Tuple.fromProductTyped(r))
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
    )(apply, bp => Tuple.fromProductTyped(bp))
}

case class GitHubTeam(
  name           : TeamName,
  lastActiveDate : Option[Instant],
  repos          : Seq[String]
) {
  val githubUrl =
    s"https://github.com/orgs/hmrc/teams/${name.asString.toLowerCase.replace(" ", "-")}"
}

object GitHubTeam {
  val format: OFormat[GitHubTeam] = {
    implicit val tnf = TeamName.format
    ( (__ \ "name"          ).format[TeamName]
    ~ (__ \ "lastActiveDate").formatNullable[Instant]
    ~ (__ \ "repos"         ).format[Seq[String]]
    )(apply, t => Tuple.fromProductTyped(t))
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

  private implicit val tf  : Format[GitHubTeam]    = GitHubTeam.format
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

  def allTeams()(implicit hc: HeaderCarrier): Future[Seq[GitHubTeam]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/v2/teams")
      .execute[Seq[GitHubTeam]]

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
