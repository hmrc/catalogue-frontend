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

package uk.gov.hmrc.cataloguefrontend.connector

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.cataloguefrontend.connector.model._
import uk.gov.hmrc.cataloguefrontend.model.SlugInfoFlag
import uk.gov.hmrc.cataloguefrontend.service.ServiceDependencies
import uk.gov.hmrc.cataloguefrontend.util.UrlUtils.encodePathParam
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class ServiceDependenciesConnector @Inject()(
  http          : HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext
) {
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  private val servicesDependenciesBaseUrl: String = servicesConfig.baseUrl("service-dependencies")

  def getDependencies(repositoryName: String)(implicit hc: HeaderCarrier): Future[Option[Dependencies]] = {
    import Dependencies.Implicits.reads
    val url = s"$servicesDependenciesBaseUrl/api/dependencies/$repositoryName"
    http
      .GET[Option[Dependencies]](url)
      .recover {
        case ex =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          None
      }
  }

  def getAllDependencies()(implicit hc: HeaderCarrier): Future[Seq[Dependencies]] = {
    import Dependencies.Implicits.reads
    val url = s"$servicesDependenciesBaseUrl/api/dependencies"
    http
      .GET[Seq[Dependencies]](url)
      .recover {
        case ex =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Nil
      }
  }

  def dependenciesForTeam(team: TeamName)(implicit hc: HeaderCarrier): Future[Seq[Dependencies]] = {
    import Dependencies.Implicits.reads
    http.GET[Seq[Dependencies]](s"$servicesDependenciesBaseUrl/api/teams/${team.asString}/dependencies")
  }

  def getSlugDependencies(
      serviceName: String
    , version    : Option[Version] = None
    )(implicit hc: HeaderCarrier
    ): Future[Seq[ServiceDependencies]] =
    http
      .GET[Seq[ServiceDependencies]](
          s"$servicesDependenciesBaseUrl/api/sluginfos"
        , queryParams = buildQueryParams(
                            "name"    -> Some(serviceName)
                          , "version" -> version.map(_.toString)
                          )
        )
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $servicesDependenciesBaseUrl/api/sluginfos: ${ex.getMessage}", ex)
          Nil
      }

  def getSlugInfo(
    serviceName: String
  , version    : Option[Version] = None
  )(implicit hc: HeaderCarrier
  ): Future[Option[ServiceDependencies]] =
    http
      .GET[Option[ServiceDependencies]](
          s"$servicesDependenciesBaseUrl/api/sluginfo"
        , queryParams = buildQueryParams(
                          "name"    -> Some(serviceName)
                        , "version" -> version.map(_.toString)
                        )
        )

  def getCuratedSlugDependencies(
    serviceName: String
  , flag       : SlugInfoFlag
  )(implicit hc: HeaderCarrier
  ): Future[Seq[Dependency]] = {
    import Dependencies.Implicits.readsDependency
    val url = s"$servicesDependenciesBaseUrl/api/slug-dependencies/${encodePathParam(serviceName)}"
    http.GET[Seq[Dependency]](
      url,
      queryParams = Seq("flag" -> flag.asString)
    ).recover {
      case NonFatal(ex) =>
        logger.error(s"An error occurred when connecting to [$url]: ${ex.getMessage}", ex)
        Nil
    }
  }

  def getCuratedSlugDependenciesForTeam(
    teamName: TeamName
  , flag    : SlugInfoFlag
  )(implicit hc: HeaderCarrier
  ): Future[Map[String, Seq[Dependency]]] = {
    import Dependencies.Implicits.readsDependency
    val url = s"$servicesDependenciesBaseUrl/api/teams/${encodePathParam(teamName.asString)}/slug-dependencies"
    http.GET[Map[String, Seq[Dependency]]](
      url
    , queryParams = Seq("flag" -> flag.asString)
    ).recover {
      case NonFatal(ex) =>
        logger.error(s"An error occurred when connecting to [$url]: ${ex.getMessage}", ex)
        Map.empty
    }
  }

  private def buildQueryParams(queryParams: (String, Option[String])*): Seq[(String, String)] =
    queryParams.collect { case (k, Some(v)) => (k, v) }

  def getServicesWithDependency(
    flag        : SlugInfoFlag,
    group       : String,
    artefact    : String,
    versionRange: BobbyVersionRange
  )(implicit hc: HeaderCarrier
  ): Future[Seq[ServiceWithDependency]] = {
    implicit val r = ServiceWithDependency.reads
    http
      .GET[Seq[ServiceWithDependency]](
        s"$servicesDependenciesBaseUrl/api/serviceDeps",
        queryParams = Seq(
          "flag"         -> flag.asString,
          "group"        -> group,
          "artefact"     -> artefact,
          "versionRange" -> versionRange.range))
   }

   def getGroupArtefacts(implicit hc: HeaderCarrier): Future[List[GroupArtefacts]] = {
     implicit val r = GroupArtefacts.apiFormat
     http.GET[List[GroupArtefacts]](s"$servicesDependenciesBaseUrl/api/groupArtefacts")
   }

  def getJDKVersions(flag: SlugInfoFlag)(implicit hc: HeaderCarrier): Future[List[JDKVersion]] = {
    implicit val r = JDKVersionFormats.jdkFormat
    http.GET[List[JDKVersion]](
      url = s"$servicesDependenciesBaseUrl/api/jdkVersions",
      queryParams = Seq("flag" -> flag.asString))
  }

  def getBobbyRuleViolations(implicit hc:HeaderCarrier): Future[Map[(BobbyRule, SlugInfoFlag), Int]] = {
    implicit val brvr = BobbyRulesSummary.reads
    http.GET[BobbyRulesSummary](url = s"$servicesDependenciesBaseUrl/api/bobbyViolations")
      .map(_.summary)
  }

  def getHistoricBobbyRuleViolations(implicit hc:HeaderCarrier): Future[HistoricBobbyRulesSummary] = {
    implicit val brvr = HistoricBobbyRulesSummary.reads
    http.GET[HistoricBobbyRulesSummary](url = s"$servicesDependenciesBaseUrl/api/historicBobbyViolations")
  }
}
