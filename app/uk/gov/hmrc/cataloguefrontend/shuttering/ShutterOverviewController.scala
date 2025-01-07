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

package uk.gov.hmrc.cataloguefrontend.shuttering

import cats.implicits._
import play.api.data.{Form, Forms}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, MessagesRequest, RequestHeader}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.config.CatalogueConfig
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, Environment, ServiceName, TeamName}
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterType.Rate
import uk.gov.hmrc.cataloguefrontend.shuttering.view.html.{FrontendRouteWarningsPage, ShutterOverviewPage}
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, IAAction, Predicate, Resource, Retrieval}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ShutterOverviewController @Inject() (
  override val mcc             : MessagesControllerComponents,
  shutterOverviewPage          : ShutterOverviewPage,
  frontendRouteWarningPage     : FrontendRouteWarningsPage,
  shutterService               : ShutterService,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  catalogueConfig              : CatalogueConfig,
  override val auth            : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  def allStates(
    shutterType   : ShutterType,
    teamName      : Option[TeamName],
    digitalService: Option[DigitalService]
   ): Action[AnyContent] =
    allStatesForEnv(
      shutterType    = shutterType,
      env            = Environment.Production,
      teamName       = teamName,
      digitalService = digitalService
    )

  def allStatesForEnv(
    shutterType   : ShutterType,
    env           : Environment,
    teamName      : Option[TeamName],
    digitalService: Option[DigitalService]
  ): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      for
        teams               <- teamsAndRepositoriesConnector.allTeams().map(_.map(_.name))
        digitalServices     <- teamsAndRepositoriesConnector.allDigitalServices()
        envAndCurrentStates <- Environment.values.toSeq.traverse: env =>
                                 shutterService
                                   .findCurrentStates(
                                    shutterType,
                                    env,
                                    teamName.filter(_.asString.nonEmpty),
                                    digitalService.filter(_.asString.nonEmpty)
                                  )
                                   .map(ws => (env, ws))
        hasGlobalPerm       <- auth
                                 .verify:
                                   Retrieval.hasPredicate(Predicate.Permission(Resource.from("shutter-api", "mdtp"), IAAction("SHUTTER")))
                                 .map(_.exists(_ == true))
        killSwitchLink      =  if hasGlobalPerm && shutterType != Rate then Some(catalogueConfig.killSwitchLink(shutterType.asString)) else None
      yield Ok(shutterOverviewPage(
        form.bindFromRequest(),
        envAndCurrentStates.toMap,
        shutterType,
        env,
        teamName,
        digitalService,
        killSwitchLink,
        teams,
        digitalServices
      ))

  case class Filter(
    team          : Option[TeamName],
    digitalService: Option[DigitalService]
  )

  lazy val form: Form[Filter] =
    Form(
      Forms.mapping(
        "teamName"       -> Forms.optional(Forms.of[TeamName]),
        "digitalService" -> Forms.optional(Forms.of[DigitalService])
      )(Filter.apply)(f => Some(Tuple.fromProductTyped(f)))
    )


  def frontendRouteWarnings(env: Environment, serviceName: ServiceName): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      for
        envsAndWarnings <- Environment.values.toSeq.traverse: env =>
                             shutterService
                               .frontendRouteWarnings(env, serviceName)
                               .map(ws => (env, ws))
        page            = frontendRouteWarningPage(envsAndWarnings.toMap, env, serviceName)
      yield Ok(page)

end ShutterOverviewController
