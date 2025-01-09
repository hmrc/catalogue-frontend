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

import cats.data.EitherT
import cats.implicits._
import play.api.data.{Form, Forms}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, ResponseHeader, Result}
import play.api.http.HttpEntity
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, ServiceType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, TeamName}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsService.KeyName
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.view.html.{ConfigExplorerPage, ConfigWarningPage, SearchConfigPage}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereService
import uk.gov.hmrc.cataloguefrontend.util.CsvUtils
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceConfigsController @Inject()(
  override val mcc        : MessagesControllerComponents
, override val auth       : FrontendAuthComponents
, teamsAndReposConnector  : TeamsAndRepositoriesConnector
, serviceConfigsService   : ServiceConfigsService
, whatsRunningWhereService: WhatsRunningWhereService
, configExplorerPage      : ConfigExplorerPage
, configWarningPage       : ConfigWarningPage
, searchConfigPage        : SearchConfigPage
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  /**
    * @param selector for reverse routing
    */
  def configExplorer(serviceName: ServiceName, showWarnings: Boolean, selector: Option[KeyName]): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for
        deployments      <- whatsRunningWhereService.releasesForService(serviceName).map(_.versions)
        configByKey      <- serviceConfigsService.configByKeyWithNextDeployment(serviceName)
        warnings         <- serviceConfigsService.configWarnings(serviceName, deployments.map(_.environment), version = None, latest = true)
        deploymentConfig <- serviceConfigsService.deploymentConfigByKeyWithNextDeployment(serviceName)
      yield Ok(configExplorerPage(serviceName, configByKey, deployments, showWarnings, warnings, deploymentConfig))
    }

  val searchLanding: Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for
        allTeams   <- teamsAndReposConnector.allTeams()
        configKeys <- serviceConfigsService.configKeys()
      yield Ok(searchConfigPage(SearchConfig.form.fill(SearchConfig.SearchConfigForm()), allTeams, configKeys))
    }

  /**
    * @param configKey for dependencyExplorer reverse routing
    */
  def searchResults(
    configKey: Option[String]
  ): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      SearchConfig
        .form
        .bindFromRequest()
        .fold(
          formWithErrors => for
                              allTeams   <- teamsAndReposConnector.allTeams()
                              configKeys <- serviceConfigsService.configKeys()
                            yield BadRequest(searchConfigPage(formWithErrors.fill(SearchConfig.SearchConfigForm()), allTeams, configKeys))
        , formObject     => (for
                              allTeams   <- EitherT.right[Result](teamsAndReposConnector.allTeams())
                              configKeys <- EitherT.right[Result](serviceConfigsService.configKeys(formObject.teamName))
                              optResults <- (formObject.teamChange, formObject.configKey, formObject.configValue) match
                                              // Do not search when only the team name has been changed
                                              case (true,  None, None) if formObject.valueFilterType != ValueFilterType.IsEmpty
                                                     => EitherT.rightT[Future, Result](Option.empty[Seq[ServiceConfigsService.AppliedConfig]])
                                              // Error when, config key or value or ValueFilterType.IsEmpty has not been specifed
                                              case (false,  None, None) if formObject.valueFilterType != ValueFilterType.IsEmpty
                                                     => EitherT.leftT[Future, Option[Seq[ServiceConfigsService.AppliedConfig]]]:
                                                          val msg = "Please search by either a config key or value."
                                                          Ok(searchConfigPage(SearchConfig.form.withGlobalError(msg).fill(formObject), allTeams, configKeys))
                                              case _ => EitherT(serviceConfigsService.configSearch(
                                                          teamName        = formObject.teamName
                                                        , environments    = formObject.showEnvironments
                                                        , serviceType     = formObject.serviceType
                                                        , key             = formObject.configKey
                                                        , keyFilterType   = KeyFilterType(formObject.configKeyIgnoreCase)
                                                        , value           = formObject.configValue
                                                        , valueFilterType = ValueFilterType(formObject.valueFilterType, formObject.configValueIgnoreCase)
                                                        )).leftMap(msg => Ok(searchConfigPage(SearchConfig.form.withGlobalError(msg).fill(formObject), allTeams, configKeys)))
                                                          .map(Option.apply)
                              (groupedByKey, groupedByService)
                                         =  (optResults, formObject.groupBy) match
                                              case (None,          _              ) => (None, None)
                                              case (Some(results), GroupBy.Key    ) => (Some(serviceConfigsService.toKeyServiceEnvironmentMap(results)), None)
                                              case (Some(results), GroupBy.Service) => (None, Some(serviceConfigsService.toServiceKeyEnvironmentMap(results)))
                             yield
                               if formObject.asCsv
                               then
                                 val rows   = formObject.groupBy match
                                                case GroupBy.Key     => toRows(groupedByKey.getOrElse(Map.empty), formObject.showEnvironments)
                                                case GroupBy.Service => toRows2(groupedByService.getOrElse(Map.empty), formObject.showEnvironments)
                                 val csv    = CsvUtils.toCsv(rows)
                                 val source = org.apache.pekko.stream.scaladsl.Source.single(org.apache.pekko.util.ByteString(csv, "UTF-8"))
                                 Result(
                                   header = ResponseHeader(200, Map("Content-Disposition" -> "inline; filename=\"config-search.csv\"")),
                                   body   = HttpEntity.Streamed(source, None, Some("text/csv"))
                                 )
                               else
                                 Ok(searchConfigPage(SearchConfig.form.fill(formObject), allTeams, configKeys, groupedByKey, groupedByService))
                            ).merge
        )
  }

  val configWarningLanding: Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for
        allTeams    <- teamsAndReposConnector.allTeams()
        allServices <- teamsAndReposConnector.allRepositories(repoType = Some(RepoType.Service), archived = Some(false))
      yield Ok(configWarningPage(ConfigWarning.form, allServices))
    }

  val configWarningResults: Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      ConfigWarning
        .form
        .bindFromRequest()
        .fold(
          _          => for
                          allServices <- teamsAndReposConnector.allRepositories(repoType = Some(RepoType.Service), archived = Some(false))
                        yield Ok(configWarningPage(ConfigWarning.form, allServices, None))
        , formObject => for
                          allServices      <- teamsAndReposConnector.allRepositories(repoType = Some(RepoType.Service), archived = Some(false))
                          deployments      <- whatsRunningWhereService.releasesForService(formObject.serviceName).map(_.versions)
                          results          <- serviceConfigsService.configWarnings(formObject.serviceName, deployments.map(_.environment), version = None, latest = true)
                          groupedByService =  serviceConfigsService.toServiceKeyEnvironmentWarningMap(results)
                        yield Ok(configWarningPage(ConfigWarning.form.fill(formObject), allServices, Some(groupedByService)))
        )
    }

  private def toRows(
    results         : Map[ServiceConfigsService.KeyName, Map[ServiceName, Map[Environment, ServiceConfigsService.ConfigSourceValue]]]
  , showEnvironments: Seq[Environment]
  ): Seq[Seq[(String, String)]] =
    for
      (key, services) <- results.toSeq
      (service, envs) <- services
    yield
      Seq("key" -> key.asString, "service" -> service.asString) ++
      showEnvironments.map(e => e.asString -> envs.get(e).map(_.value).getOrElse(""))

  private def toRows2(
    results         : Map[ServiceName, Map[ServiceConfigsService.KeyName, Map[Environment, ServiceConfigsService.ConfigSourceValue]]]
  , showEnvironments: Seq[Environment]
  ): Seq[Seq[(String, String)]] =
    for
      (service, keys) <- results.toSeq
      (key, envs)     <- keys
    yield
      Seq("service" -> service.asString, "key" -> key.asString) ++
      showEnvironments.map(e => e.asString -> envs.get(e).map(_.value).getOrElse(""))

end ServiceConfigsController

object ConfigWarning {
  case class ConfigWarningForm(
    serviceName: ServiceName
  )

  lazy val form: Form[ConfigWarningForm] = Form(
    Forms.mapping(
      "serviceName" -> Forms.of[ServiceName]
    )(ConfigWarningForm.apply)(r => Some(r.serviceName))
  )
}

object SearchConfig {
  case class SearchConfigForm(
    teamName             : Option[TeamName]    = None
  , configKey            : Option[String]      = None
  , configKeyIgnoreCase  : Boolean             = true
  , configValue          : Option[String]      = None
  , configValueIgnoreCase: Boolean             = true
  , valueFilterType      : FormValueFilterType = FormValueFilterType.Contains
  , showEnvironments     : Seq[Environment]    = Environment.values.toSeq.filterNot(_ == Environment.Integration)
  , serviceType          : Option[ServiceType] = None
  , teamChange           : Boolean             = false
  , asCsv                : Boolean             = false
  , groupBy              : GroupBy             = GroupBy.Key
  )

  lazy val form: Form[SearchConfigForm] =
    Form(
      Forms.mapping(
        "teamName"              -> Forms.optional(Forms.of[TeamName])
      , "configKey"             -> Forms.optional(Forms.nonEmptyText(minLength = 3))
      , "configKeyIgnoreCase"   -> Forms.default(Forms.boolean, false)
      , "configValue"           -> Forms.optional(Forms.text)
      , "configValueIgnoreCase" -> Forms.default(Forms.boolean, false)
      , "valueFilterType"       -> Forms.default(Forms.of[FormValueFilterType], FormValueFilterType.Contains)
      , "showEnvironments"      -> Forms.seq(Forms.of[Environment])
      , "serviceType"           -> Forms.optional(Forms.of[ServiceType])
      , "teamChange"            -> Forms.default(Forms.boolean, false)
      , "asCsv"                 -> Forms.default(Forms.boolean, false)
      , "groupBy"               -> Forms.default(Forms.of[GroupBy], GroupBy.Key)
      )(SearchConfigForm.apply)(f => Some(Tuple.fromProductTyped(f)))
    )
}
