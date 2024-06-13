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

package uk.gov.hmrc.cataloguefrontend.costs

import cats.implicits._
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.http.HttpEntity
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimateConfig
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.DeploymentConfig
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.cataloguefrontend.util.CsvUtils
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.costs.CostExplorerPage

import java.time.Instant
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class CostsSummaryController @Inject() (
  serviceConfigsConnector      : ServiceConfigsConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  costExplorerPage             : CostExplorerPage,
  costEstimateConfig           : CostEstimateConfig,
  override val mcc             : MessagesControllerComponents,
  override val auth            : FrontendAuthComponents
)(implicit
  override val ec              : ExecutionContext
) extends FrontendController(mcc)
    with CatalogueAuthBuilders {

  private def toRows(serviceDeploymentMap: Map[String, Seq[DeploymentConfig]]): Seq[Seq[(String, String)]] = {
    serviceDeploymentMap.map {
      case (serviceName, configList) =>

        val configEstimateCosts = String.format("%.0f", configList.map(_.deploymentSize.totalSlots.costGbp(costEstimateConfig)).sum)

        val slotsAndInstances = Environment.values.flatMap {
          env =>
            Seq(
              s"slots.{${env.asString}}" -> configList.find(_.environment == env).map(_.deploymentSize.slots.toString).getOrElse(""),
              s"instances.{${env.asString}}" -> configList.find(_.environment == env).map(_.deploymentSize.instances.toString).getOrElse("")
            )
        }

        Seq("Application Name" -> serviceName) ++ slotsAndInstances ++ Seq("Estimated Cost (£ / year)" -> configEstimateCosts)
    }.toSeq
  }

  def costExplorer(
    team : Option[String] = None,
    asCSV: Boolean        = false
  ): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        teams           <- teamsAndRepositoriesConnector.allTeams().map(_.sortBy(_.name.asString))
        configs         <- serviceConfigsConnector.deploymentConfig(team = team.filterNot(_.trim.isEmpty))
        groupedConfigs  = configs.groupBy(_.serviceName)
      } yield {
        if (asCSV) {
          val csv = s"${team.getOrElse("")} ,Integration, ,Development, ,QA, ,Staging, ,ExternalTest, ,Production\n" + // CSV header
            CsvUtils.toCsv(toRows(groupedConfigs)).replaceAll(""".\{.*?(})""", "")
          val source = Source.single(ByteString(csv, "UTF-8"))

          Result(
            header = ResponseHeader(200, Map("Content-Disposition" -> s"inline; filename=\"cost-explorer-${Instant.now()}.csv\"")),
            body = HttpEntity.Streamed(source, None, Some("text/csv"))
          )
        } else {
          Ok(costExplorerPage(groupedConfigs, teams, RepoListFilter.form.bindFromRequest(), costEstimateConfig))
        }
      }
    }
}

object RepoListFilter {
  lazy val form: Form[RepoListFilter] =
    Form(
      mapping(
        "team" -> optional(text).transform[Option[TeamName]](_.filter(_.trim.nonEmpty).map(TeamName.apply), _.map(_.asString))
      )(RepoListFilter.apply)(r => Some(r.team))
    )
}

case class RepoListFilter(team: Option[TeamName] = None)
