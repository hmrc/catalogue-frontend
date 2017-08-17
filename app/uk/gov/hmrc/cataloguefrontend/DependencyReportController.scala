/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import akka.stream.scaladsl._
import org.apache.commons.io.IOUtils
import play.api.http.HttpEntity
import play.api.libs.json.Json
import play.api.mvc._
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.cataloguefrontend.TeamsAndRepositoriesConnector.TeamsAndRepositoriesError
import uk.gov.hmrc.cataloguefrontend.connector.ServiceDependenciesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependencies, Version}
import uk.gov.hmrc.cataloguefrontend.report.{DependencyReport, DependencyReportRepository, MongoDependencyReportRepository}
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object DependencyReportController extends DependencyReportController with MongoDbConnection {

  override def teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector = TeamsAndRepositoriesConnector

  lazy override val dependencyReportRepository = new MongoDependencyReportRepository(db)

  lazy override val serviceDependencyConnector: ServiceDependenciesConnector = ServiceDependenciesConnector
}

trait DependencyReportController extends FrontendController with UserManagementPortalLink {

  val profileBaseUrlConfigKey = "user-management.profileBaseUrl"

  val repotypeToDetailsUrl = Map(
    RepoType.Service -> routes.CatalogueController.service _,
    RepoType.Other -> routes.CatalogueController.repository _,
    RepoType.Library -> routes.CatalogueController.library _,
    RepoType.Prototype -> routes.CatalogueController.prototype _
  )

  def teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector

  def serviceDependencyConnector: ServiceDependenciesConnector

  def dependencyReportRepository: DependencyReportRepository


  implicit val drFormat = Json.format[DependencyReport]

  def getDependencyReport = Action.async {
    dependencyReportRepository.getAllDependencyReports.map(d => Ok(Json.toJson(d)))
  }



  private def persistDependencies(digitalServices: Seq[Either[TeamsAndRepositoriesError, DigitalService]],
                                  allRepos: Seq[RepositoryDisplayDetails],
                                  allTeams: Seq[Team],
                                  dependencies: Dependencies) = {

    val repoName = dependencies.repositoryName
    dependencies.libraryDependenciesState.map { d =>

      val report = DependencyReport(repository = repoName,
                                    repoType = getRepositoryType(repoName, allRepos),
                                    team = findTeamNames(repoName, allTeams).mkString(";"),
                                    digitalService = findDigitalServiceName(repoName, digitalServices),
                                    dependencyName = d.libraryName,
                                    dependencyType = "library",
                                    currentVersion = d.currentVersion.toString,
                                    latestVersion = d.latestVersion.getOrElse("Unknown").toString,
                                    colour = getColour(d.currentVersion, d.latestVersion))


      dependencyReportRepository.add(report)
      report
    }

  }

  def toCsv(deps: Seq[DependencyReport], ignoreFields: Seq[String]): String = {

    def ccToMap(cc: AnyRef) =
      cc.getClass.getDeclaredFields.foldLeft(Map[String, Any]()) { (acc, field) =>
        if(ignoreFields.contains(field.getName)) {
          acc
        } else {
          field.setAccessible(true)
          acc + (field.getName -> field.get(cc))
        }
      }
    val dependencyDataMaps = deps.map(ccToMap)
    val headers = dependencyDataMaps.headOption.map{row => row.keys.mkString(",")}
    val dataRows = dependencyDataMaps.map{row => row.values.mkString(",")}

    headers.map(x => (x +: dataRows).mkString("\n")).getOrElse("No data")

  }



  def getRepositoryType(repositoryName:String, allRepos: Seq[RepositoryDisplayDetails]): String =
    allRepos.find(r => r.name == repositoryName).map(_.repoType).getOrElse("Unknown").toString

  def findTeamNames(repositoryName: String, teams: Seq[Team]): Seq[String] =
    teams.filter(_.repos.isDefined).filter(team => team.repos.get.values.flatten.toSeq.contains(repositoryName)).map(_.name)

  def findDigitalServiceName(repositoryName: String, errorsOrDigitalServices: Seq[Either[TeamsAndRepositoriesError, DigitalService]]): String =
    errorsOrDigitalServices
      .filter(errorOrDigitalService => errorOrDigitalService.isRight)
      .find(_.right.get.repositories.exists(_.name == repositoryName))
      .map(_.right.get.name).getOrElse("Unknown")

  def getColour(currentVersion: Version, mayBeLatestVersion: Option[Version]): String = {
    mayBeLatestVersion.fold("grey") { latestVersion =>
      latestVersion - currentVersion match {
        case (0, 0, 0) => "green"
        case (0, minor, patch) if minor != 0 || patch != 0 => "amber"
        case (major, _, _) if major >= 1 => "red"
        case _ => "N/A"
      }
    }
  }


  def dependencyReport() = Action.async { implicit request =>
    type RepoName = String

    val allTeamsF = teamsAndRepositoriesConnector.teamsWithRepositories()
    val digitalServicesF = teamsAndRepositoriesConnector.allDigitalServices.map(_.data).flatMap { digitalServices =>
      Future.sequence {
        digitalServices.map(teamsAndRepositoriesConnector.digitalServiceInfo)
      }.map(errorsOrDigitalServices => errorsOrDigitalServices.map(errorOrDigitalService => errorOrDigitalService.right.map(_.data)))
    }

    val eventualDependencyReports = for {
      _ <- dependencyReportRepository.clearAllData
      allRepos <- teamsAndRepositoriesConnector.allRepositories.map(_.data)
      allTeams: Seq[Team] <- allTeamsF
      digitalServices <- digitalServicesF
      allDependencies <- serviceDependencyConnector.getAllDependencies
    } yield {
      allDependencies.flatMap { dependencies =>
        persistDependencies(digitalServices, allRepos, allTeams, dependencies)
      }
    }

    eventualDependencyReports.map { deps =>
      val source = StreamConverters.fromInputStream(() => IOUtils.toInputStream(toCsv(deps, Seq("timestamp")), "UTF-8"))
      Result(
        header = ResponseHeader(200, Map.empty),
        body = HttpEntity.Streamed(source, None, Some("text/csv"))
      )
    }
  }



}









