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
import views.html.serviceconfigs.{ConfigExplorerPage, SearchConfigByKeyPage}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceConfigsController @Inject()(
  override val mcc        : MessagesControllerComponents
, override val auth       : FrontendAuthComponents
, configuration           : Configuration
, teamsAndReposConnector  : TeamsAndRepositoriesConnector
, serviceConfigsService   : ServiceConfigsService
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
        configByKey <- serviceConfigsService.configByKey(serviceName)
      } yield Ok(serviceConfigPage(serviceName, configByKey, deployments, appConfigBaseInSlug))
    }

  def searchByKeyLanding(): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        allTeams   <- teamsAndReposConnector.allTeams()
        configKeys <- serviceConfigsService.configKeys()
      } yield Ok(configSearchPage(ConfigSearch.form.fill(ConfigSearch()), configKeys, allTeams))
    }

  def searchByKeyResults(): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      ConfigSearch
        .form
        .bindFromRequest()
        .fold(
          formWithErrors => for {
                              allTeams   <- teamsAndReposConnector.allTeams()
                              configKeys <- serviceConfigsService.configKeys()
                            } yield BadRequest(configSearchPage(formWithErrors.fill(ConfigSearch()), configKeys, allTeams))
        , formObject     => for {
                              allTeams   <- teamsAndReposConnector.allTeams()
                              configKeys <- serviceConfigsService.configKeys(formObject.teamName)
                              results    <- formObject.configKey match {
                                              case Some(k) => serviceConfigsService.searchAppliedConfig(k, formObject.teamName)
                                              case None    => Future.successful(Nil)
                                            }
                              filtered   =  formObject.configValue.fold(results) { v =>
                                              formObject.valueFiterType match {
                                                case ValueFilterType.Contains       => results.filter(_.value.contains(v))
                                                case ValueFilterType.DoesNotContain => results.filterNot(_.value.contains(v))
                                                case ValueFilterType.EqualTo        => results.filter(_.value == v)
                                              }
                                            }
                              configMap  =  serviceConfigsService.toKeyServiceEnviromentMap(filtered)
                            } yield
                              if (formObject.asCsv) {
                                val csv    = CsvUtils.toCsv(toRows(configMap, formObject.showEnviroments))
                                val source = akka.stream.scaladsl.Source.single(akka.util.ByteString(csv, "UTF-8"))
                                Result(
                                  header = ResponseHeader(200, Map("Content-Disposition" -> "inline; filename=\"config-search.csv\"")),
                                  body   = HttpEntity.Streamed(source, None, Some("text/csv"))
                                )
                            } else {
                              Ok(configSearchPage(ConfigSearch.form.fill(formObject), configKeys, allTeams, formObject.configKey.map(_ => configMap)))
                            }
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
}

case class ConfigSearch(
  teamName       : Option[TeamName]  = None
, configKey      : Option[String]    = None
, configValue    : Option[String]    = None
, valueFiterType : ValueFilterType   = ValueFilterType.Contains
, showEnviroments: List[Environment] = Environment.values.filterNot(_ == Environment.Integration)
, asCsv          : Boolean           = false
)

sealed trait ValueFilterType {val asString: String; val displayString: String; }
object ValueFilterType {
  case object Contains       extends ValueFilterType { val asString = "contains";       val displayString = "Contains"         }
  case object DoesNotContain extends ValueFilterType { val asString = "doesNotContain"; val displayString = "Does not contain" }
  case object EqualTo        extends ValueFilterType { val asString = "equalTo";        val displayString = "Equal to" }

  val values: List[ValueFilterType]             = List(Contains, DoesNotContain, EqualTo)
  def parse(s: String): Option[ValueFilterType] = values.find(_.asString == s)
}

object ConfigSearch {
  import play.api.data.{Form, Forms, FormError}
  import play.api.data.format.Formatter
  private def valueFilterTypeFormat: Formatter[ValueFilterType] = new Formatter[ValueFilterType] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], ValueFilterType] =
      data
        .get(key)
        .flatMap(ValueFilterType.parse)
        .fold[Either[Seq[FormError], ValueFilterType]](Left(Seq(FormError(key, "Invalid filter type"))))(Right.apply)

    override def unbind(key: String, value: ValueFilterType): Map[String, String] =
      Map(key -> value.asString)
  }

  lazy val form: Form[ConfigSearch] = Form(
    Forms.mapping(
      "teamName"        -> Forms.optional(Forms.text.transform[TeamName](TeamName.apply, _.asString))
    , "configKey"       -> Forms.optional(Forms.nonEmptyText(minLength = 3))
    , "configValue"     -> Forms.optional(Forms.text)
    , "valueFilterType" -> Forms.of[ValueFilterType](valueFilterTypeFormat)
    , "showEnviroments" -> Forms.list(Forms.text)
                                .transform[List[Environment]](
                                  xs => { val ys = xs.map(Environment.parse).flatten
                                          if (ys.nonEmpty) ys else Environment.values.filterNot(_ == Environment.Integration) // populate environments for config explorer link
                                        }
                                , x  => identity(x).map(_.asString)
                                )
    , "asCsv"           -> Forms.boolean
    )(ConfigSearch.apply)(ConfigSearch.unapply)
  )
}
