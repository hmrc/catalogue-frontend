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
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.cataloguefrontend.config.Constant
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, TeamName}
import uk.gov.hmrc.cataloguefrontend.util.{FromString, FromStringEnum, Parser, FormFormat}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import FromStringEnum._

given Parser[RepoType] = Parser.parser(RepoType.values)

enum Organisation(
  override val asString: String
) extends FromString
  derives Reads:
  case Mdtp                   extends Organisation("mdtp")
  case External(name: String) extends Organisation(name)

given Parser[Organisation] =
    (s: String) =>
      s.toLowerCase match
        case Organisation.Mdtp.asString => Right(Organisation.Mdtp)
        case o                          => Right(Organisation.External(o))

enum RepoType(
  override val asString: String
) extends FromString
  derives Ordering, Reads, Writes, FormFormat:
  case Service   extends RepoType("Service"  )
  case Library   extends RepoType("Library"  )
  case Prototype extends RepoType("Prototype")
  case Test      extends RepoType("Test"     )
  case Other     extends RepoType("Other"    )

given Parser[ServiceType] = Parser.parser(ServiceType.values)

enum ServiceType(
  override val asString: String,
  val displayString    : String
) extends FromString
  derives Ordering, Reads, Writes, FormFormat, QueryStringBindable:
  case Frontend extends ServiceType(asString = "frontend", displayString = "Frontend")
  case Backend  extends ServiceType(asString = "backend" , displayString = "Backend")

given Parser[Tag] = Parser.parser(Tag.values)

enum Tag(
  override val asString: String,
  val displayString    : String
) extends FromString
  derives Ordering, Reads:
  case AdminFrontend    extends Tag(asString = "admin"             , displayString = "Admin Frontend"    )
  case Api              extends Tag(asString = "api"               , displayString = "API"               )
  case External         extends Tag(asString = "external"          , displayString = "External"          )
  case BuiltOffPlatform extends Tag(asString = "built-off-platform", displayString = "Built Off Platform")
  case Maven            extends Tag(asString = "maven"             , displayString = "Maven"             )
  case Stub             extends Tag(asString = "stub"              , displayString = "Stub"              )


case class Link(
  name       : String,
  displayName: String,
  url        : String,
  cls        : Option[String] = None
):
  val id: String =
    displayName.toLowerCase.replaceAll(" ", "-")

object Link:
  val reads: Reads[Link] =
    ( (__ \ "name"       ).read[String]
    ~ (__ \ "displayName").read[String]
    ~ (__ \ "url"        ).read[String]
    ~ (__ \ "cls"        ).readNullable[String]
    )(apply)

case class SecurityAssessmentBreakdown(
  high         : Int,
  medium       : Int,
  low          : Int,
  informational: Int
)

object SecurityAssessmentBreakdown:
  val reads: Reads[SecurityAssessmentBreakdown] =
    ( (__ \ "High"         ).read[Int]
    ~ (__ \ "Medium"       ).read[Int]
    ~ (__ \ "Low"          ).read[Int]
    ~ (__ \ "Informational").read[Int]
    )(SecurityAssessmentBreakdown.apply _)

case class TestJobResults(
  numAccessibilityViolations : Option[Int],
  numSecurityAlerts          : Option[Int],
  securityAssessmentBreakdown: Option[SecurityAssessmentBreakdown]
)

object TestJobResults:
  given Reads[SecurityAssessmentBreakdown] = SecurityAssessmentBreakdown.reads
  val reads: Reads[TestJobResults] =
    ( (__ \ "numAccessibilityViolations" ).readNullable[Int]
    ~ (__ \ "numSecurityAlerts"          ).readNullable[Int]
    ~ (__ \ "securityAssessmentBreakdown").readNullable[SecurityAssessmentBreakdown]
    )(TestJobResults.apply _)

case class BuildData(
  number        : Int,
  url           : String,
  timestamp     : Instant,
  result        : Option[String],
  description   : Option[String],
  testJobResults: Option[TestJobResults] = None
)

object BuildData:
  val reads: Reads[BuildData] =
    ( (__ \ "number"        ).read[Int]
    ~ (__ \ "url"           ).read[String]
    ~ (__ \ "timestamp"     ).read[Instant]
    ~ (__ \ "result"        ).readNullable[String]
    ~ (__ \ "description"   ).readNullable[String]
    ~ (__ \ "testJobResults").readNullable[TestJobResults](TestJobResults.reads)
  )(apply)

given Parser[BuildJobType] = Parser.parser(BuildJobType.values)

enum BuildJobType(
  override val asString: String
) extends FromString
  derives Ordering, Reads:
  case Job         extends BuildJobType("job"         )
  case Test        extends BuildJobType("test"        )
  case Pipeline    extends BuildJobType("pipeline"    )
  case PullRequest extends BuildJobType("pull-request")

case class JenkinsJob(
  repoName   : String,
  jobName    : String,
  jenkinsURL : String,
  jobType    : BuildJobType,
  testType   : Option[TestType],
  latestBuild: Option[BuildData]
)

given Parser[TestType] = Parser.parser(TestType.values)

enum TestType(
  override val asString: String
) extends FromString
  derives Ordering, Reads:
  case Acceptance  extends TestType("Acceptance")
  case Performance extends TestType("Performance")
  case Contract    extends TestType("Contract")

object JenkinsJob:
  val reads: Reads[JenkinsJob] =
    ( (__ \ "repoName"   ).read        [String]
    ~ (__ \ "jobName"    ).read        [String]
    ~ (__ \ "jenkinsURL" ).read        [String]
    ~ (__ \ "jobType"    ).read        [BuildJobType]
    ~ (__ \ "testType"   ).readNullable[TestType]
    ~ (__ \ "latestBuild").readNullable[BuildData   ](BuildData.reads)
  )(apply)

case class GitRepository(
  name                : String,
  organisation        : Option[Organisation],
  description         : String,
  githubUrl           : String,
  createdDate         : Instant,
  lastActiveDate      : Instant,
  endOfLifeDate       : Option[Instant]          = None,
  isPrivate           : Boolean                  = false,
  repoType            : RepoType                 = RepoType.Other,
  serviceType         : Option[ServiceType]      = None,
  tags                : Option[Set[Tag]]         = None,
  digitalServiceName  : Option[DigitalService]   = None,
  owningTeams         : Seq[TeamName]            = Seq.empty,
  language            : Option[String],
  isArchived          : Boolean,
  defaultBranch       : String,
  branchProtection    : Option[BranchProtection] = None,
  isDeprecated        : Boolean                  = false,
  teamNames           : Seq[TeamName]            = Seq.empty,
  prototypeName       : Option[String]           = None,
  prototypeAutoPublish: Option[Boolean]          = None,
):
  def displayName: String =
    name + organisation.filterNot(_ == Organisation.Mdtp).fold("")(x => s" (${x.asString})")

  def isShared: Boolean =
    teamNames.length >= Constant.sharedRepoTeamsCutOff

  def isDeFactoOwner(teamName: TeamName): Boolean =
    owningTeams.contains(teamName) && owningTeams.toSet != teamNames.toSet

  val descriptionAboveLimit: Boolean =
    description.length > 512

end GitRepository

object GitRepository:
  val reads: Reads[GitRepository] =
    ( (__ \ "name"                ).read[String]
    ~ (__ \ "organisation"        ).readNullable[Organisation]
    ~ (__ \ "description"         ).read[String]
    ~ (__ \ "url"                 ).read[String]
    ~ (__ \ "createdDate"         ).read[Instant]
    ~ (__ \ "lastActiveDate"      ).read[Instant]
    ~ (__ \ "endOfLifeDate"       ).readNullable[Instant]
    ~ (__ \ "isPrivate"           ).readWithDefault[Boolean](false)
    ~ (__ \ "repoType"            ).read[RepoType]
    ~ (__ \ "serviceType"         ).readNullable[ServiceType]
    ~ (__ \ "tags"                ).readNullable[Set[Tag]]
    ~ (__ \ "digitalServiceName"  ).readNullable[DigitalService]
    ~ (__ \ "owningTeams"         ).readWithDefault[Seq[TeamName]](Seq.empty)
    ~ (__ \ "language"            ).readNullable[String]
    ~ (__ \ "isArchived"          ).readWithDefault[Boolean](false)
    ~ (__ \ "defaultBranch"       ).read[String]
    ~ (__ \ "branchProtection"    ).readNullable(BranchProtection.reads)
    ~ (__ \ "isDeprecated"        ).readWithDefault[Boolean](false)
    ~ (__ \ "teamNames"           ).readWithDefault[Seq[TeamName]](Seq.empty)
    ~ (__ \ "prototypeName"       ).readNullable[String]
    ~ (__ \ "prototypeAutoPublish").readNullable[Boolean]
    )(apply)

case class BranchProtection(
  requiresApprovingReviews: Boolean,
  dismissesStaleReviews   : Boolean,
  requiresCommitSignatures: Boolean
):
  def isProtected: Boolean =
    requiresApprovingReviews && dismissesStaleReviews && requiresCommitSignatures

object BranchProtection:

  val reads: Reads[BranchProtection] =
    ( (__ \ "requiresApprovingReviews").read[Boolean]
    ~ (__ \ "dismissesStaleReviews"   ).read[Boolean]
    ~ (__ \ "requiresCommitSignatures").read[Boolean]
    )(apply)

case class GitHubTeam(
  name           : TeamName,
  lastActiveDate : Option[Instant],
  repos          : Seq[String]
):
  val githubUrl =
    s"https://github.com/orgs/hmrc/teams/${name.asString.toLowerCase.replace(" ", "-")}"

object GitHubTeam:
  val reads: Reads[GitHubTeam] =
    ( (__ \ "name"          ).read[TeamName]
    ~ (__ \ "lastActiveDate").readNullable[Instant]
    ~ (__ \ "repos"         ).read[Seq[String]]
    )(apply)

case class OpenPullRequest(
  repoName : String,
  title    : String,
  url      : String,
  author   : String,
  createdAt: Instant
)

object OpenPullRequest:
  val reads: Reads[OpenPullRequest] =
    ( (__ \ "repoName" ).read[String]
    ~ (__ \ "title"    ).read[String]
    ~ (__ \ "url"      ).read[String]
    ~ (__ \ "author"   ).read[String]
    ~ (__ \ "createdAt").read[Instant]
    )(apply)

@Singleton
class TeamsAndRepositoriesConnector @Inject()(
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using ExecutionContext):
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  private val teamsAndServicesBaseUrl: String =
    servicesConfig.baseUrl("teams-and-repositories")

  private given Reads[GitHubTeam]    = GitHubTeam.reads
  private given Reads[GitRepository] = GitRepository.reads // v2 model
  private given Reads[OpenPullRequest] = OpenPullRequest.reads

  def findTestJobs(
    teamName      : Option[TeamName]       = None,
    digitalService: Option[DigitalService] = None
  )(using HeaderCarrier): Future[Seq[JenkinsJob]] =
    given Reads[JenkinsJob] =
      JenkinsJob.reads

    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/test-jobs?teamName=${teamName.map(_.asString)}&digitalService=${digitalService.map(_.asString)}")
      .execute[Seq[JenkinsJob]]

  def lookupLatestJenkinsJobs(service: String)(using HeaderCarrier): Future[Seq[JenkinsJob]] =
    given Reads[Seq[JenkinsJob]] =
      Reads.at(__ \ "jobs")(Reads.seq(JenkinsJob.reads))

    val url = url"$teamsAndServicesBaseUrl/api/v2/repositories/$service/jenkins-jobs"
    httpClientV2
      .get(url)
      .execute[Seq[JenkinsJob]]
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty[JenkinsJob]
      }

  def findRelatedTestRepos(service: String)(using HeaderCarrier): Future[Seq[String]] =
    val url = url"$teamsAndServicesBaseUrl/api/v2/repositories/$service/test-repositories"

    httpClientV2
      .get(url)
      .execute[Seq[String]]
      .recover:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty[String]

  def findServicesUnderTest(testRepo: String)(using HeaderCarrier): Future[Seq[String]] =
    val url = url"$teamsAndServicesBaseUrl/api/v2/repositories/$testRepo/services-under-test"

    httpClientV2
      .get(url)
      .execute[Seq[String]]
      .recover:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty[String]

  def openPullRequestsForReposOwnedByTeam(teamName: TeamName)(using HeaderCarrier): Future[Seq[OpenPullRequest]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/open-pull-requests?reposOwnedByTeam=${teamName.asString}")
      .execute[Seq[OpenPullRequest]]

  def openPullRequestsForReposOwnedByDigitalService(digitalService: DigitalService)(using HeaderCarrier): Future[Seq[OpenPullRequest]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/open-pull-requests?reposOwnedByDigitalService=${digitalService.asString}")
      .execute[Seq[OpenPullRequest]]

  def openPullRequestsRaisedByMembersOfTeam(teamName: TeamName)(using HeaderCarrier): Future[Seq[OpenPullRequest]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/open-pull-requests?raisedByMembersOfTeam=${teamName.asString}")
      .execute[Seq[OpenPullRequest]]

  def allTeams(teamName: Option[TeamName] = None)(using HeaderCarrier): Future[Seq[GitHubTeam]] =
     httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/v2/teams?name=${teamName.map(_.asString)}")
      .execute[Seq[GitHubTeam]]

  def allDigitalServices()(using HeaderCarrier): Future[Seq[DigitalService]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/v2/digital-services")
      .execute[Seq[DigitalService]]

  def repositoriesForTeam(
    teamName       : TeamName,
    includeArchived: Option[Boolean] = None
  )(using HeaderCarrier): Future[Seq[GitRepository]] =
    val url = url"$teamsAndServicesBaseUrl/api/v2/repositories?owningTeam=${teamName.asString}&archived=$includeArchived"

    httpClientV2
      .get(url)
      .execute[Seq[GitRepository]]
      .recover:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Nil

  def allRepositories(
     name          : Option[String]         = None,
     team          : Option[TeamName]       = None,
     digitalService: Option[DigitalService] = None,
     archived      : Option[Boolean]        = None,
     repoType      : Option[RepoType]       = None,
     serviceType   : Option[ServiceType]    = None
   )(using HeaderCarrier): Future[Seq[GitRepository]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/v2/repositories?name=$name&owningTeam=${team.map(_.asString)}&digitalServiceName=${digitalService.map(_.asString)}&archived=$archived&repoType=${repoType.map(_.asString)}&serviceType=${serviceType.map(_.asString)}")
      .execute[Seq[GitRepository]]
      .map(_.sortBy(_.name))

  def repositoryDetails(name: String)(using HeaderCarrier): Future[Option[GitRepository]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/v2/repositories/$name")
      .execute[Option[GitRepository]]

  def allTeamsByService()(using HeaderCarrier): Future[Map[String, Seq[TeamName]]] =
    for
      repos          <- httpClientV2
                          .get(url"$teamsAndServicesBaseUrl/api/v2/repositories")
                          .execute[Seq[GitRepository]]
      teamsByService =  repos.map(r => r.name -> r.teamNames).toMap
    yield teamsByService

  def enableBranchProtection(repoName: String)(using HeaderCarrier): Future[Unit] =
    httpClientV2
      .post(url"$teamsAndServicesBaseUrl/api/v2/repositories/$repoName/branch-protection/enabled")
      .withBody(JsBoolean(true))
      .execute[Unit](HttpReads.Implicits.throwOnFailure(summon[HttpReads[Either[UpstreamErrorResponse, Unit]]]), summon[ExecutionContext])
