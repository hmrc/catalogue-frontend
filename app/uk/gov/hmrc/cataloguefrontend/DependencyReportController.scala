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

import java.util.Date
import javax.inject.{Inject, Singleton}

import akka.stream.scaladsl._
import org.apache.commons.io.IOUtils
import play.api.http.HttpEntity
import play.api.libs.json.Json
import play.api.mvc._
import play.api.{Configuration, Environment => PlayEnvironment}
import uk.gov.hmrc.cataloguefrontend.TeamsAndRepositoriesConnector.TeamsAndRepositoriesError
import uk.gov.hmrc.cataloguefrontend.connector.ServiceDependenciesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependencies, Version}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class DependencyReport(repository: String,
                            team: String,
                            digitalService: String,
                            dependencyName: String,
                            dependencyType: String,
                            currentVersion: String,
                            latestVersion: String,
                            colour: String,
                            timestamp: Long = new Date().getTime)

@Singleton
class DependencyReportController @Inject()(http : HttpClient,
                                           override val runModeConfiguration:Configuration,
                                           environment : PlayEnvironment,
                                           teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
                                           serviceDependencyConnector: ServiceDependenciesConnector
                                          ) extends FrontendController with UserManagementPortalLink {


  override protected def mode = environment.mode


  implicit val drFormat = Json.format[DependencyReport]


  private def getDependencies(digitalServices: Seq[Either[TeamsAndRepositoriesError, DigitalService]],
                              allTeams: Seq[Team],
                              dependencies: Dependencies) = {

    val repoName = dependencies.repositoryName

    val libraryDependencyReportLines = dependencies.libraryDependenciesState.map { d =>
      DependencyReport(repository = repoName,
        team = findTeamNames(repoName, allTeams).mkString(";"),
        digitalService = findDigitalServiceName(repoName, digitalServices),
        dependencyName = d.libraryName,
        dependencyType = "library",
        currentVersion = d.currentVersion.toString,
        latestVersion = d.latestVersion.getOrElse("Unknown").toString,
        colour = getColour(d.currentVersion, d.latestVersion))
    }

    val sbtPluginDependencyReportLines = dependencies.sbtPluginsDependenciesState.map { d =>

      DependencyReport(repository = repoName,
        team = findTeamNames(repoName, allTeams).mkString(";"),
        digitalService = findDigitalServiceName(repoName, digitalServices),
        dependencyName = d.sbtPluginName,
        dependencyType = "plugin",
        currentVersion = d.currentVersion.toString,
        latestVersion = d.latestVersion.getOrElse("Unknown").toString,
        colour = getColour(d.currentVersion, d.latestVersion))
    }

    libraryDependencyReportLines ++ sbtPluginDependencyReportLines
  }

  private def toCsv(deps: Seq[DependencyReport], ignoreFields: Seq[String]): String = {

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

  private def findTeamNames(repositoryName: String, teams: Seq[Team]): Seq[String] =
    teams.filter(_.repos.isDefined).filter(team => team.repos.get.values.flatten.toSeq.contains(repositoryName)).map(_.name)

  private def findDigitalServiceName(repositoryName: String, errorsOrDigitalServices: Seq[Either[TeamsAndRepositoriesError, DigitalService]]): String =
    errorsOrDigitalServices
      .filter(errorOrDigitalService => errorOrDigitalService.isRight)
      .find(_.right.get.repositories.exists(_.name == repositoryName))
      .map(_.right.get.name).getOrElse("Unknown")

  private def getColour(currentVersion: Version, mayBeLatestVersion: Option[Version]): String = {
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
    val digitalServicesF = teamsAndRepositoriesConnector.allDigitalServices.flatMap { digitalServices =>
      Future.sequence {
        digitalServices.map(teamsAndRepositoriesConnector.digitalServiceInfo)
      }.map(errorsOrDigitalServices => errorsOrDigitalServices.map(errorOrDigitalService => errorOrDigitalService.right.map(identity)))
    }

    val eventualDependencyReports = for {
      allTeams: Seq[Team] <- allTeamsF
      digitalServices <- digitalServicesF
      allDependencies <- serviceDependencyConnector.getAllDependencies
    } yield {
      allDependencies.flatMap { dependencies =>
        getDependencies(digitalServices, allTeams, dependencies)
      }
    }

    eventualDependencyReports.map { deps =>
      val csv = toCsv(deps, Seq("timestamp"))
      val source = StreamConverters.fromInputStream(() => {
        IOUtils.toInputStream(csv, "UTF-8")
      })
      Result(
        header = ResponseHeader(200, Map.empty),
        body = HttpEntity.Streamed(source, None, Some("text/csv"))
      )
    }
  }
}
