/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.service

import java.time.LocalDateTime

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector.{ServiceName, TeamName}
import uk.gov.hmrc.cataloguefrontend.{Deployer, Release, ServiceDeploymentInformation, ServiceDeploymentsConnector}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

case class TeamRelease(
  name: ServiceName,
  teams: Seq[TeamName],
  productionDate: LocalDateTime,
  creationDate: Option[LocalDateTime] = None,
  interval: Option[Long]              = None,
  leadTime: Option[Long]              = None,
  version: String,
  latestDeployer: Option[Deployer] = None)

@Singleton
class DeploymentsService @Inject()(
  serviceDeploymentsConnector: ServiceDeploymentsConnector,
  teamsAndServicesConnector: TeamsAndRepositoriesConnector) {
  type ServiceTeamMappings = Map[ServiceName, Seq[TeamName]]

  sealed trait ReleaseFilter { def serviceTeams: ServiceTeamMappings }
  final case class ServiceTeams(serviceTeams: ServiceTeamMappings) extends ReleaseFilter
  final case class All(serviceTeams: ServiceTeamMappings) extends ReleaseFilter
  case object NotFound extends ReleaseFilter { val serviceTeams: ServiceTeamMappings = Map.empty }

  def getDeployments(teamName: Option[TeamName], serviceName: Option[ServiceName])(
    implicit hc: HeaderCarrier): Future[Seq[TeamRelease]] =
    for {
      query <- buildFilter(teamName, serviceName)
      deployments <- query match {
                      case All(_) =>
                        serviceDeploymentsConnector.getDeployments()
                      case ServiceTeams(st) =>
                        serviceDeploymentsConnector.getDeployments(st.keySet)
                      case NotFound =>
                        Future.successful(Seq.empty)
                    }
    } yield deployments map teamRelease(query)

  def getWhatsRunningWhere(serviceName: String)(
    implicit hc: HeaderCarrier): Future[Either[Throwable, ServiceDeploymentInformation]] =
    serviceDeploymentsConnector.getWhatIsRunningWhere(serviceName)

  private def teamRelease(rq: ReleaseFilter)(r: Release) =
    TeamRelease(
      r.name,
      rq.serviceTeams.getOrElse(r.name, Seq.empty),
      productionDate = r.productionDate,
      creationDate   = r.creationDate,
      interval       = r.interval,
      leadTime       = r.leadTime,
      version        = r.version,
      r.latestDeployer
    )

  private def buildFilter(teamName: Option[TeamName], serviceName: Option[ServiceName])(
    implicit hc: HeaderCarrier): Future[ReleaseFilter] =
    buildFilterFromService(serviceName)
      .orElse(buildFilterFromTeam(teamName))
      .getOrElse(emptyFilter)

  def buildFilterFromService(serviceName: Option[ServiceName])(
    implicit hc: HeaderCarrier): Option[Future[ReleaseFilter]] =
    serviceName map { s =>
      for (service <- teamsAndServicesConnector.repositoryDetails(s))
        yield
          service map { s =>
            ServiceTeams(Map(s.name -> s.teamNames))
          } getOrElse NotFound
    }

  def buildFilterFromTeam(teamName: Option[TeamName])(implicit hc: HeaderCarrier): Option[Future[ReleaseFilter]] =
    teamName map { t =>
      teamsAndServicesConnector.teamInfo(t).flatMap {
        case Some(team) =>
          val teamServiceNames = team.repos.getOrElse(Map.empty)(RepoType.Service.toString)
          teamsAndServicesConnector.teamsByService(teamServiceNames).map { st =>
            ServiceTeams(st)
          }
        case None => Future.successful(NotFound)
      }
    }

  def emptyFilter(implicit hc: HeaderCarrier): Future[ReleaseFilter] =
    teamsAndServicesConnector.allTeamsByService().map { cached =>
      All(cached)
    }
}
