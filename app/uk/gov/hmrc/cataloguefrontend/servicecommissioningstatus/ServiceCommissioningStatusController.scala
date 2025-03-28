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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, ResponseHeader, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{ServiceType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, Environment, ServiceName, TeamName}
import uk.gov.hmrc.cataloguefrontend.util.CsvUtils
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.view.html.{SearchServiceCommissioningStatusPage, ServiceCommissioningStatusPage}
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

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
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  def getCommissioningState(serviceName: ServiceName): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for
        lifecycleStatus <- serviceCommissioningStatusConnector.getLifecycle(serviceName).map(_.map(_.lifecycleStatus).getOrElse(LifecycleStatus.Active))
        results         <- serviceCommissioningStatusConnector.commissioningStatus(serviceName)
      yield
        Ok(serviceCommissioningStatusPage(serviceName, lifecycleStatus, results))
    }

  val searchLanding: Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for
        allTeams        <- teamsAndRepositoriesConnector.allTeams()
        digitalServices <- teamsAndRepositoriesConnector.allDigitalServices()
        allChecks       <- serviceCommissioningStatusConnector.allChecks()
        form            =  SearchCommissioning.searchForm.fill(
                             SearchCommissioning.SearchCommissioningForm(
                               teamName           = None
                             , digitalService     = None
                             , serviceType        = None
                             , lifecycleStatus    = LifecycleStatus.values.toSeq
                             , checks             = allChecks.map(_._1)
                             , environments       = Environment.values.toSeq.filterNot(_ == Environment.Integration)
                             , groupByEnvironment = Option(false)
                             , warningFilter      = Option(false)
                             )
                           )
      yield Ok(searchServiceCommissioningStatusPage(form, allTeams, digitalServices, allChecks))
    }

  /**
    * @param teamName for reverse routing
    * @param digitalService for reverse routing
    * @param hasWarning for reverse routing
    */
  def searchResults(teamName: Option[TeamName], digitalService: Option[DigitalService], hasWarning: Option[Boolean]): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      SearchCommissioning
        .searchForm
        .bindFromRequest()
        .fold(
          formWithErrors => for
                              allTeams        <- teamsAndRepositoriesConnector.allTeams()
                              digitalServices <- teamsAndRepositoriesConnector.allDigitalServices()
                              allChecks       <- serviceCommissioningStatusConnector.allChecks()
                            yield BadRequest(searchServiceCommissioningStatusPage(formWithErrors, allTeams, digitalServices, allChecks))
        , formObject     => for
                              allTeams        <- teamsAndRepositoriesConnector.allTeams()
                              digitalServices <- teamsAndRepositoriesConnector.allDigitalServices()
                              allChecks       <- serviceCommissioningStatusConnector.allChecks()
                              checks          =  if formObject.checks.isEmpty then allChecks.map(_._1).toList else formObject.checks
                              allResults      <- serviceCommissioningStatusConnector.cachedCommissioningStatus(formObject.teamName, formObject.digitalService, formObject.serviceType, formObject.lifecycleStatus)
                              results         =  allResults.filter: result => //we show all services with any present checks since this can indicate where they haven't been fully decommissioned
                                                   val hasChecks = result.checks.exists:
                                                     case check: Check.SimpleCheck => check.checkResult.isRight
                                                     case check: Check.EnvCheck    => check.checkResults.values.exists(_.isRight)
                                                   val hasWarnings = result.warnings.nonEmpty
                                                   hasWarnings || (!formObject.warningFilter.getOrElse(false) && hasChecks)
                            yield
                              if formObject.asCsv
                              then
                                val rows   = toRows(allChecks.filter { case (title, _) => checks.contains(title) }, formObject.environments, results)
                                val csv    = CsvUtils.toCsv(rows)
                                val source = org.apache.pekko.stream.scaladsl.Source.single(org.apache.pekko.util.ByteString(csv, "UTF-8"))
                                Result(
                                  header = ResponseHeader(200, Map("Content-Disposition" -> "inline; filename=\"commissioning-state.csv\"")),
                                  body   = HttpEntity.Streamed(source, None, Some("text/csv"))
                                )
                              else
                                Ok(searchServiceCommissioningStatusPage(SearchCommissioning.searchForm.fill(formObject.copy(checks = checks)), allTeams, digitalServices, allChecks, Some(results)))
        )
    }

  private def toRows(
    checks      : Seq[(String, FormCheckType)]
  , environments: Seq[Environment]
  , results     : Seq[CachedServiceCheck]
  ): Seq[Seq[(String, String)]] =
    for
      result <- results
    yield
      Seq("service" -> result.serviceName.asString)
         ++ checks.flatMap:
              case (title, formCheckType) =>
                (formCheckType, result.checks.find(_.title == title)) match
                  case (FormCheckType.Simple     , None)                       => Seq(title -> displayResult(None))
                  case (FormCheckType.Environment, None)                       => environments.flatMap { e => Seq(s"$title - ${e.asString}" -> displayResult(None)) }
                  case (FormCheckType.Simple     , Some(c: Check.SimpleCheck)) => Seq(title -> displayResult(Some(c.checkResult)))
                  case (FormCheckType.Environment, Some(c: Check.EnvCheck))    => environments.flatMap { e => Seq(s"$title - ${e.asString}" -> displayResult(c.checkResults.get(e))) }
                  case _                                                       => Nil

  private def displayResult(result: Option[Check.Result]) =
    result match
      case None                 => ""
      case Some(Right(present)) => "Y"
      case Some(Left(missing))  => "N"

end ServiceCommissioningStatusController

import play.api.data.{Form, Forms}

object SearchCommissioning {
  case class SearchCommissioningForm(
    teamName          : Option[TeamName]
  , digitalService    : Option[DigitalService]
  , serviceType       : Option[ServiceType]
  , lifecycleStatus   : Seq[LifecycleStatus]
  , checks            : Seq[String]
  , environments      : Seq[Environment]
  , asCsv             : Boolean = false
  , groupByEnvironment: Option[Boolean] = None
  , warningFilter     : Option[Boolean] = None
  )

  lazy val searchForm: Form[SearchCommissioningForm] =
    Form(
      Forms.mapping(
        "team"               -> Forms.optional(Forms.of[TeamName])
      , "digitalService"     -> Forms.optional(Forms.of[DigitalService])
      , "serviceType"        -> Forms.optional(Forms.of[ServiceType])
      , "lifecycleStatus"    -> Forms.default(
                                  Forms.seq(Forms.of[LifecycleStatus])
                                , LifecycleStatus.values.toSeq
                                )
      , "checks"             -> Forms.seq(Forms.text)
      , "environments"       -> Forms.default(
                                  Forms.seq(Forms.of[Environment])
                                , Environment.values.toSeq.filterNot(_ == Environment.Integration)
                                )
      , "asCsv"              -> Forms.boolean
      , "groupByEnvironment" -> Forms.optional(Forms.boolean)
      , "warningFilter"      -> Forms.optional(Forms.boolean)
      )(SearchCommissioningForm.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

  case class TeamCommissioningForm(
    checkType: String
  )

  lazy val teamForm: Form[TeamCommissioningForm] =
    Form(
      Forms.mapping(
        "checkType" -> Forms.text
      )(TeamCommissioningForm.apply)(r => Some(r.checkType))
    )
}
