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

import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.model.Environment._
import uk.gov.hmrc.cataloguefrontend.service.CostEstimateConfig
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.DeploymentConfig
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.costs.CostsSummaryPage

import java.text.NumberFormat
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class CostsSummaryController @Inject() (
  serviceConfigsConnector: ServiceConfigsConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  costsSummaryPage: CostsSummaryPage,
  costEstimateConfig: CostEstimateConfig,
  override val mcc: MessagesControllerComponents,
  override val auth: FrontendAuthComponents
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
    with CatalogueAuthBuilders {

  def onPageLoad(team: Option[String] = None): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>

      for {
        teams   <- teamsAndRepositoriesConnector.allTeams().map(_.sortBy(_.name.asString))
        configs <- serviceConfigsConnector.deploymentConfig(team = team.filterNot(_.trim.isEmpty))
      } yield {
        val viewModel: Seq[ServiceAndEnvironment] = ServiceAndEnvironment(configs.groupBy(_.serviceName))
        Ok(costsSummaryPage(viewModel, teams, RepoListFilter.form.bindFromRequest(), costEstimateConfig))
      }
    }
}

object RepoListFilter {
  lazy val form: Form[RepoListFilter] =
    Form(
      mapping(
        "team" -> optional(text).transform[Option[TeamName]](_.filter(_.trim.nonEmpty).map(TeamName.apply), _.map(_.asString))
      )(RepoListFilter.apply)(RepoListFilter.unapply)
    )
}

case class ServiceAndEnvironment(serviceName: String, configs: Seq[DeploymentConfig]) {

  val getIntegrationEnvironment: Option[DeploymentConfig]   = configs.find(_.environment == Integration)
  val getDevelopmentEnvironment: Option[DeploymentConfig]   = configs.find(_.environment == Development)
  val getQAEnvironment: Option[DeploymentConfig]            = configs.find(_.environment == QA)
  val getStagingEnvironment: Option[DeploymentConfig]       = configs.find(_.environment == Staging)
  val getExternalTestEnvironment: Option[DeploymentConfig]  = configs.find(_.environment == ExternalTest)
  val getProductionEnvironment: Option[DeploymentConfig]    = configs.find(_.environment == Production)

  def totalEstimatedCost(costEstimateConfig: CostEstimateConfig): Double = {
    configs.map(_.deploymentSize.totalSlots.costGbp(costEstimateConfig)).sum
  }

  def totalEstimatedCostFormatted(costEstimateConfig: CostEstimateConfig): String = {
    val formatter: NumberFormat = java.text.NumberFormat.getIntegerInstance
    formatter.format(totalEstimatedCost(costEstimateConfig))
  }

}

object ServiceAndEnvironment {
  def apply(deploymentConfigs: Map[String, Seq[DeploymentConfig]]): Seq[ServiceAndEnvironment] = {
    deploymentConfigs.map {
      case (name, configs) => ServiceAndEnvironment(name, configs)
    }.toSeq
  }
}

case class RepoListFilter(team: Option[TeamName] = None)
