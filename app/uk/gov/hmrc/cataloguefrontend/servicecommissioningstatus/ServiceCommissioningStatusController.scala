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

package uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus

import play.api.http.HttpEntity
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result, ResponseHeader}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.connector.{TeamsAndRepositoriesConnector, ServiceType}
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.cataloguefrontend.util.CsvUtils
import views.html.error_404_template
import views.html.servicecommissioningstatus.{ServiceCommissioningStatusPage, SearchServiceCommissioningStatusPage}


import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext


@Singleton
class ServiceCommissioningStatusController @Inject() (
  serviceCommissioningStatusConnector : ServiceCommissioningStatusConnector
, teamsAndRepositoriesConnector       : TeamsAndRepositoriesConnector
, serviceCommissioningStatusPage      : ServiceCommissioningStatusPage
, searchServiceCommissioningStatusPage: SearchServiceCommissioningStatusPage
, override val mcc                    : MessagesControllerComponents
, override val auth                   : FrontendAuthComponents
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  def getCommissioningState(serviceName: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      serviceCommissioningStatusConnector
        .commissioningStatus(serviceName)
        .map(_.fold(NotFound(error_404_template()))(result => Ok(serviceCommissioningStatusPage(serviceName, result))))
    }

  def searchLanding(): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        allTeams  <- teamsAndRepositoriesConnector.allTeams()
        allChecks <- serviceCommissioningStatusConnector.allChecks()
        form      =  SearchCommissioning.searchForm.fill(
                       SearchCommissioning.SearchCommissioningForm(
                         teamName      = None
                       , serviceType   = None
                       , checks        = allChecks.map(_._1).toList
                       , environments  = Environment.values.filterNot(_ == Environment.Integration)
                       )
                     )
      } yield Ok(searchServiceCommissioningStatusPage(form, allTeams, allChecks))
    }

  def searchResults(
    teamName: Option[TeamName] = None,
    serviceType: Option[ServiceType] = None,
    checks: List[String] = List.empty,
    environments: List[Environment] = List.empty): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      SearchCommissioning
        .searchForm
        .bindFromRequest()
        .fold(
          formWithErrors => for {
                              allTeams  <- teamsAndRepositoriesConnector.allTeams()
                              allChecks <- serviceCommissioningStatusConnector.allChecks()
                            } yield BadRequest(searchServiceCommissioningStatusPage(formWithErrors, allTeams, allChecks))
        , formObject     => for {
                              allTeams  <- teamsAndRepositoriesConnector.allTeams()
                              allChecks <- serviceCommissioningStatusConnector.allChecks()
                              results   <- serviceCommissioningStatusConnector.cachedCommissioningStatus(formObject.teamName, formObject.serviceType)
                            } yield if (formObject.asCsv) {
                                val rows   = toRows(allChecks.filter { case (title, _) => formObject.checks.contains(title) }, formObject.environments, results)
                                val csv    = CsvUtils.toCsv(rows)
                                val source = org.apache.pekko.stream.scaladsl.Source.single(org.apache.pekko.util.ByteString(csv, "UTF-8"))
                                Result(
                                  header = ResponseHeader(200, Map("Content-Disposition" -> "inline; filename=\"commissioning-state.csv\"")),
                                  body   = HttpEntity.Streamed(source, None, Some("text/csv"))
                                )
                            } else {
                              Ok(searchServiceCommissioningStatusPage(SearchCommissioning.searchForm.fill(formObject), allTeams, allChecks, Some(results)))
                            }
        )
    }

  private def toRows(
    checks      : Seq[(String, FormCheckType)]
  , environments: Seq[Environment]
  , results     : Seq[CachedServiceCheck]
  ): Seq[Seq[(String, String)]] =
    for {
      result <- results
    } yield {
      Seq("service" -> result.serviceName.asString) ++ checks.flatMap {case (title, formCheckType) =>
        (formCheckType, result.checks.find(_.title == title)) match {
          case (FormCheckType.Simple     , None)                       => Seq(title -> displayResult(None))
          case (FormCheckType.Environment, None)                       => environments.flatMap { e => Seq(s"$title - ${e.asString}" -> displayResult(None)) }
          case (FormCheckType.Simple     , Some(c: Check.SimpleCheck)) => Seq(title -> displayResult(Some(c.checkResult)))
          case (FormCheckType.Environment, Some(c: Check.EnvCheck))    => environments.flatMap { e => Seq(s"$title - ${e.asString}" -> displayResult(c.checkResults.get(e))) }
          case _                                                       => Nil
        }
      }
    }

  private def displayResult(result: Option[Check.Result]) = result match {
    case None                 => ""
    case Some(Right(present)) => "Y"
    case Some(Left(missing))  => "N"
  }
}

import play.api.data.{Form, Forms}

object SearchCommissioning {
  case class SearchCommissioningForm(
    teamName    : Option[TeamName]
  , serviceType : Option[ServiceType]
  , checks      : List[String]
  , environments: List[Environment]
  , asCsv       : Boolean = false
  )

  lazy val searchForm: Form[SearchCommissioningForm] = Form(
    Forms.mapping(
      "teamName"     -> Forms.optional(Forms.text.transform[TeamName](TeamName.apply, _.asString))
    , "serviceType"  -> Forms.text.transform[Option[ServiceType]](x => ServiceType.parse(x).toOption, _.fold("")(_.asString))
    , "checks"       -> Forms.list(Forms.text)
    , "environments" -> Forms.list(Forms.text)
                             .transform[List[Environment]](
                               xs => xs.map(Environment.parse).flatten
                             , x  => identity(x).map(_.asString)
                             )
    , "asCsv"        -> Forms.boolean
    )(SearchCommissioningForm.apply)(SearchCommissioningForm.unapply)
  )

  case class TeamCommissioningForm(
    checkType: String
  )

  lazy val teamForm: Form[TeamCommissioningForm] = Form(
    Forms.mapping(
      "checkType" -> Forms.text
    )(TeamCommissioningForm.apply)(TeamCommissioningForm.unapply)
  )

}
