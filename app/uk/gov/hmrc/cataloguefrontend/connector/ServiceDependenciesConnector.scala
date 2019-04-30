/*
 * Copyright 2019 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.service.ServiceDependencies
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

sealed trait SlugInfoFlag { def s: String }
object SlugInfoFlag {
  case object Latest     extends SlugInfoFlag { val s = "latest"     }
  case object Production extends SlugInfoFlag { val s = "production" }
  case object QA         extends SlugInfoFlag { val s = "qa"         }

  val values = List(Latest, Production, QA)

  def parse(s: String): Option[SlugInfoFlag] =
    values.find(_.s == s)
}

@Singleton
class ServiceDependenciesConnector @Inject()(
  http          : HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {

  private val servicesDependenciesBaseUrl: String = servicesConfig.baseUrl("service-dependencies") + "/api"

  def getDependencies(repositoryName: String)(implicit hc: HeaderCarrier): Future[Option[Dependencies]] = {
    implicit val reads = Dependencies.reads
    http
      .GET[Option[Dependencies]](s"$servicesDependenciesBaseUrl/dependencies/$repositoryName")
      .recover {
        case ex =>
          Logger.error(s"An error occurred when connecting to $servicesDependenciesBaseUrl: ${ex.getMessage}", ex)
          None
      }
  }

  def getAllDependencies()(implicit hc: HeaderCarrier): Future[Seq[Dependencies]] = {
    implicit val reads = Dependencies.reads
    http
      .GET[Seq[Dependencies]](s"$servicesDependenciesBaseUrl/dependencies")
      .recover {
        case ex =>
          Logger.error(s"An error occurred when connecting to $servicesDependenciesBaseUrl: ${ex.getMessage}", ex)
          Nil
      }
  }

  def dependenciesForTeam(team: String)(implicit hc: HeaderCarrier): Future[Seq[Dependencies]] = {
    implicit val reads = Dependencies.reads
    http.GET[Seq[Dependencies]](s"$servicesDependenciesBaseUrl/teams/$team/dependencies")
  }

  def getSlugDependencies(serviceName: String, version: Option[String] = None)
                         (implicit hc: HeaderCarrier): Future[Seq[ServiceDependencies]] = {
    val queryParams = buildQueryParams(
      "name"    -> Some(serviceName),
      "version" -> version)

    http
      .GET[Seq[ServiceDependencies]](s"$servicesDependenciesBaseUrl/sluginfos", queryParams)
      .recover {
        case NonFatal(ex) =>
          Logger.error(s"An error occurred when connecting to $servicesDependenciesBaseUrl/sluginfos: ${ex.getMessage}", ex)
          Nil
      }
  }

  def getServicesWithDependency(
      flag    : SlugInfoFlag,
      group   : String,
      artefact: String)(implicit hc: HeaderCarrier): Future[Seq[ServiceWithDependency]] = {
    implicit val r = ServiceWithDependency.reads
    http
      .GET[Seq[ServiceWithDependency]](
        s"$servicesDependenciesBaseUrl/serviceDeps",
        queryParams = Seq(
          "flag"     -> flag.s,
          "group"    -> group,
          "artefact" -> artefact))
   }

   def getGroupArtefacts(implicit hc: HeaderCarrier): Future[List[GroupArtefacts]] = {
     implicit val r = GroupArtefacts.apiFormat
     http.GET[List[GroupArtefacts]](s"$servicesDependenciesBaseUrl/groupArtefacts")
   }

  def getJDKVersions(flag: SlugInfoFlag)(implicit hc: HeaderCarrier): Future[List[JDKVersion]] = {
    implicit val r = JDKVersionFormats.jdkFormat
    http.GET[List[JDKVersion]](url = s"$servicesDependenciesBaseUrl/jdkVersions?flag=$flag")
  }

  private def buildQueryParams(queryParams: (String, Option[String])*): Seq[(String, String)] =
    queryParams.flatMap(param => param._2.map(v => (param._1, v)))
}
