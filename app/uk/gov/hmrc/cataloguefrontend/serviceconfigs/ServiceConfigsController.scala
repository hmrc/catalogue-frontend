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

package uk.gov.hmrc.cataloguefrontend.serviceconfigs

import play.api.Configuration
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereService
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.serviceconfigs.{ConfigExplorerPage, SearchConfigByKeyPage}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceConfigsController @Inject()(
  override val mcc        : MessagesControllerComponents
, override val auth       : FrontendAuthComponents
, configuration           : Configuration
, serviceconfigsService   : ServiceConfigsService
, whatsRunningWhereService: WhatsRunningWhereService
, serviceConfigPage       : ConfigExplorerPage
, configSearchPage        : SearchConfigByKeyPage
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  private val appConfigBaseInSlug: Map[ServiceConfigsService.ConfigEnvironment, Boolean] =
    Environment.values
      .map(env => ServiceConfigsService.ConfigEnvironment.ForEnvironment(env) -> configuration.get[Boolean](s"app-config-base-in-slug.${env.asString}"))
      .toMap

  def configExplorer(serviceName: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        deployments <- whatsRunningWhereService.releasesForService(serviceName).map(_.versions)
        configByKey <- serviceconfigsService.configByKey(serviceName)
      } yield Ok(serviceConfigPage(serviceName, configByKey, deployments, appConfigBaseInSlug))
    }

  def searchByKey(): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        configKeys <- serviceconfigsService.configKeys()
        result     <- ConfigSearch
                        .form
                        .bindFromRequest()
                        .fold(
                          formWithErrors => Future.successful(Ok(configSearchPage(formWithErrors.fill(ConfigSearch()), configKeys)))
                        , formWithObject => serviceconfigsService
                                              .searchAppliedConfig(formWithObject.key)
                                              .map { results => Ok(configSearchPage(ConfigSearch.form.fill(formWithObject), configKeys, Some(results))) }
                        )
      } yield result
    }
}

case class ConfigSearch(
  key            : String            = ""
, showEnviroments: List[Environment] = Environment.values.filterNot(_ == Environment.Integration)
)

object ConfigSearch {
  import play.api.data.{Form, Forms}

  lazy val form: Form[ConfigSearch] = Form(
    Forms.mapping(
      "key"             -> Forms.text.verifying("Search term must be at least 3 characters", _.length >= 3)
    , "showEnviroments" -> Forms.list(Forms.text)
                                .transform[List[Environment]](
                                  xs => { val ys = xs.map(Environment.parse).flatten
                                          if (ys.nonEmpty) ys else Environment.values.filterNot(_ == Environment.Integration) // populate environments for config explorer link
                                        }
                                , x  => identity(x).map(_.asString)
                                )
    )(ConfigSearch.apply)(ConfigSearch.unapply)
  )
}
