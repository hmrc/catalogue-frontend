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

package uk.gov.hmrc.cataloguefrontend.cost

import cats.data.OptionT
import cats.implicits._
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.data.{Form, Forms}
import play.api.data.Forms.{mapping, optional}
import play.api.http.HttpEntity
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.cost.view.html.{CostEstimationPage, CostExplorerPage}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, TeamName}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.cataloguefrontend.util.CsvUtils
import uk.gov.hmrc.cataloguefrontend.view.html.error_404_template
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import java.time.Instant
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class CostController @Inject() (
  serviceConfigsConnector      : ServiceConfigsConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  costEstimationService        : CostEstimationService,
  costExplorerPage             : CostExplorerPage,
  costEstimationPage           : CostEstimationPage,
  costEstimateConfig           : CostEstimateConfig,
  override val mcc             : MessagesControllerComponents,
  override val auth            : FrontendAuthComponents
)(using
  override val ec              : ExecutionContext
) extends FrontendController(mcc)
    with CatalogueAuthBuilders:

  private def toRows(serviceDeploymentMap: Map[ServiceName, Seq[DeploymentConfig]]): Seq[Seq[(String, String)]] =
    serviceDeploymentMap
      .map: (serviceName, configList) =>
        val configEstimateCosts = String.format("%.0f", configList.map(_.deploymentSize.totalSlots.costGbp(costEstimateConfig)).sum)

        val slotsAndInstances =
          Environment.valuesAsSeq.flatMap: env =>
            Seq(
              s"slots.{${env.asString}}" -> configList.find(_.environment == env).map(_.deploymentSize.slots.toString).getOrElse(""),
              s"instances.{${env.asString}}" -> configList.find(_.environment == env).map(_.deploymentSize.instances.toString).getOrElse("")
            )

        Seq("Application Name" -> serviceName.asString) ++ slotsAndInstances ++ Seq("Estimated Cost (£ / year)" -> configEstimateCosts)
      .toSeq

  def costExplorer(
    team : Option[TeamName] = None,
    asCSV: Boolean          = false
  ): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for
        teams           <- teamsAndRepositoriesConnector.allTeams().map(_.sortBy(_.name.asString))
        configs         <- serviceConfigsConnector.deploymentConfig(team = team.filterNot(_.asString.trim.isEmpty))
        groupedConfigs  =  configs.groupBy(_.serviceName)
      yield
        if asCSV
        then
          val csv = s"${team.fold("")(_.asString)} ,Integration, ,Development, ,QA, ,Staging, ,ExternalTest, ,Production\n" + // CSV header
            CsvUtils.toCsv(toRows(groupedConfigs)).replaceAll(""".\{.*?(})""", "")
          val source = Source.single(ByteString(csv, "UTF-8"))

          Result(
            header = ResponseHeader(200, Map("Content-Disposition" -> s"inline; filename=\"cost-explorer-${Instant.now()}.csv\"")),
            body = HttpEntity.Streamed(source, None, Some("text/csv"))
          )
        else
          Ok(costExplorerPage(groupedConfigs, teams, RepoListFilter.form.bindFromRequest(), costEstimateConfig))
      }

  def costEstimation(serviceName: ServiceName): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      (for
         repositoryDetails           <- OptionT(teamsAndRepositoriesConnector.repositoryDetails(serviceName.asString))
         if repositoryDetails.repoType == RepoType.Service
         serviceCostEstimation              <- OptionT.liftF(costEstimationService.estimateServiceCost(serviceName))
         historicEstimatedCostCharts <- OptionT.liftF(costEstimationService.historicEstimatedCostChartsForService(serviceName))
       yield
         Ok(costEstimationPage(
           serviceName,
           repositoryDetails,
           serviceCostEstimation,
           costEstimateConfig,
           historicEstimatedCostCharts
         ))
      ).getOrElse(NotFound(error_404_template()))
    }


case class RepoListFilter(
  team: Option[TeamName] = None
)

object RepoListFilter:
  val form: Form[RepoListFilter] =
    Form(
      mapping(
        "team" -> optional(Forms.of[TeamName](TeamName.formFormat))
      )(RepoListFilter.apply)(r => Some(r.team))
    )
