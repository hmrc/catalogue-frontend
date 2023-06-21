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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result, ResponseHeader}
import play.api.http.HttpEntity
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereService
import uk.gov.hmrc.cataloguefrontend.util.CsvUtils
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.serviceconfigs.{ConfigExplorerPage, SearchConfigPage}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{Future, ExecutionContext}

@Singleton
class ServiceConfigsController @Inject()(
  override val mcc        : MessagesControllerComponents
, override val auth       : FrontendAuthComponents
, configuration           : Configuration
, teamsAndReposConnector  : TeamsAndRepositoriesConnector
, serviceConfigsService   : ServiceConfigsService
, whatsRunningWhereService: WhatsRunningWhereService
, configExplorerPage      : ConfigExplorerPage
, searchConfigPage        : SearchConfigPage
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  def configExplorer(serviceName: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        deployments <- whatsRunningWhereService.releasesForService(serviceName).map(_.versions)
        configByKey <- serviceConfigsService.configByKey(serviceName)
      } yield Ok(configExplorerPage(serviceName, configByKey, deployments))
    }

  def searchLanding(): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        allTeams   <- teamsAndReposConnector.allTeams()
        configKeys <- serviceConfigsService.configKeys()
      } yield Ok(searchConfigPage(SearchConfig.form.fill(SearchConfig.SearchConfigForm()), allTeams, configKeys))
    }

  import cats.data.EitherT
  import cats.instances.future._
  def searchResults(): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      SearchConfig
        .form
        .bindFromRequest()
        .fold(
          formWithErrors => for {
                              allTeams   <- teamsAndReposConnector.allTeams()
                              configKeys <- serviceConfigsService.configKeys()
                            } yield BadRequest(searchConfigPage(formWithErrors.fill(SearchConfig.SearchConfigForm()), allTeams, configKeys))
        , formObject     => (for {
                              allTeams   <- EitherT.right[Result](teamsAndReposConnector.allTeams())
                              configKeys <- EitherT.right[Result](serviceConfigsService.configKeys(formObject.teamName))
                              results    <- (formObject.configKey, formObject.configValue) match {
                                              case (None, None) if formObject.valueFilterType != ValueFilterType.IsEmpty
                                                     => EitherT.rightT[Future, Result](Nil)
                                              case _ => EitherT(serviceConfigsService.searchAppliedConfig(formObject.teamName, formObject.serviceType, formObject.configKey, formObject.configValue, Some(formObject.valueFilterType)))
                                                          .leftMap(msg => Ok(searchConfigPage(SearchConfig.form.withGlobalError(msg).fill(formObject), allTeams, configKeys)))
                                            }
                              (groupedByKey, groupedByService)
                                         =  formObject.groupBy match {
                                              case GroupBy.Key     => (Some(serviceConfigsService.toKeyServiceEnviromentMap(results)), None)
                                              case GroupBy.Service => (None, Some(serviceConfigsService.toServiceKeyEnviromentMap(results)))
                                            }
                            } yield
                              if (formObject.asCsv) {
                                val rows   = formObject.groupBy match {
                                               case GroupBy.Key     => toRows(groupedByKey.getOrElse(Map.empty), formObject.showEnviroments)
                                               case GroupBy.Service => toRows2(groupedByService.getOrElse(Map.empty), formObject.showEnviroments)
                                             }
                                val csv    = CsvUtils.toCsv(rows)
                                val source = akka.stream.scaladsl.Source.single(akka.util.ByteString(csv, "UTF-8"))
                                Result(
                                  header = ResponseHeader(200, Map("Content-Disposition" -> "inline; filename=\"config-search.csv\"")),
                                  body   = HttpEntity.Streamed(source, None, Some("text/csv"))
                                )
                            } else {
                              Ok(searchConfigPage(SearchConfig.form.fill(formObject), allTeams, configKeys, groupedByKey, groupedByService))
                            }).merge
        )
    }

  private def toRows(
    results        : Map[ServiceConfigsService.KeyName, Map[ServiceConfigsService.ServiceName, Map[Environment, Option[String]]]]
  , showEnviroments: Seq[Environment]
  ): Seq[Seq[(String, String)]] =
    for {
      (key, services) <- results.toSeq
      (service, envs) <- services
    } yield
      Seq("key" -> key.asString, "service" -> service.asString) ++
      showEnviroments.map(e => e.asString -> envs.get(e).flatten.getOrElse(""))

  private def toRows2(
    results        : Map[ServiceConfigsService.ServiceName, Map[ServiceConfigsService.KeyName, Map[Environment, Option[String]]]]
  , showEnviroments: Seq[Environment]
  ): Seq[Seq[(String, String)]] =
    for {
      (service, keys) <- results.toSeq
      (key, envs)     <- keys
    } yield
      Seq("service" -> service.asString, "key" -> key.asString) ++
      showEnviroments.map(e => e.asString -> envs.get(e).flatten.getOrElse(""))

}
object SearchConfig {
  import play.api.data.{Form, Forms}

  case class SearchConfigForm(
    teamName       : Option[TeamName]    = None
  , configKey      : Option[String]      = None
  , configValue    : Option[String]      = None
  , valueFilterType: ValueFilterType     = ValueFilterType.Contains
  , showEnviroments: List[Environment]   = Environment.values.filterNot(_ == Environment.Integration)
  , serviceType    : Option[ServiceType] = None
  , asCsv          : Boolean             = false
  , groupBy        : GroupBy             = GroupBy.Key
  )

  lazy val form: Form[SearchConfigForm] = Form(
    Forms.mapping(
      "teamName"        -> Forms.optional(Forms.text.transform[TeamName](TeamName.apply, _.asString))
    , "configKey"       -> Forms.optional(Forms.nonEmptyText(minLength = 3))
    , "configValue"     -> Forms.optional(Forms.text)
    , "valueFilterType" -> Forms.of[ValueFilterType](ValueFilterType.formFormat)
    , "showEnviroments" -> Forms.list(Forms.text)
                                .transform[List[Environment]](
                                  xs => { val ys = xs.map(Environment.parse).flatten
                                          if (ys.nonEmpty) ys else Environment.values.filterNot(_ == Environment.Integration) // populate environments for config explorer link
                                        }
                                , x  => identity(x).map(_.asString)
                                )
    , "serviceType"     -> Forms.optional(Forms.of[ServiceType](ServiceType.formFormat))
    , "asCsv"           -> Forms.boolean
    , "groupBy"         -> Forms.of[GroupBy](GroupBy.formFormat)
    )(SearchConfigForm.apply)(SearchConfigForm.unapply)
  )
}
