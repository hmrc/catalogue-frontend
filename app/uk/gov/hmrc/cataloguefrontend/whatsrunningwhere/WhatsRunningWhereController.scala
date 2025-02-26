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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import cats.data.EitherT
import play.api.data.{Form, Forms}
import play.api.i18n.Messages.implicitMessagesProviderToMessages
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, MessagesRequest, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, ServiceName, TeamName}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.view.html.WhatsRunningWherePage
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhatsRunningWhereController @Inject() (
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
, whatsRunningWhereService     : WhatsRunningWhereService
, whatsRunningWherePage        : WhatsRunningWherePage
, config                       : WhatsRunningWhereServiceConfig,
  override val mcc  : MessagesControllerComponents,
  override val auth : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  /**
    * @param teamName for reverse routing
    * @param digitalService for reverse routing
    */
  def releases(teamName: Option[TeamName], digitalService: Option[DigitalService]): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      ( for
         teamNames       <- EitherT.right[Result](teamsAndRepositoriesConnector.allTeams())
         digitalServices <- EitherT.right[Result](teamsAndRepositoriesConnector.allDigitalServices())
         sm2Profiles     <- EitherT.right[Result](whatsRunningWhereService.sm2Profiles())
         form            =  WhatsRunningWhereFilter.form.bindFromRequest()
         filter          <- EitherT.fromEither[Future](form.fold(
                              formErrors => Left(BadRequest(whatsRunningWherePage(formErrors, teamNames, digitalServices, sm2Profiles, config.maxMemoryAmount, ViewMode.Versions)))
                            , formObject => Right(formObject)
                            ))
         releases        <- EitherT.right[Result](whatsRunningWhereService.releases(filter.teamName, filter.digitalService, filter.sm2Profile))
         versionResults  <- filter.viewMode match
                              case ViewMode.Versions => EitherT.pure[Future, Result](releases)
                              case _                 => EitherT.pure[Future, Result](Nil)
         instanceResults  <- filter.viewMode match
                              case ViewMode.Instances => EitherT.right[Result](whatsRunningWhereService.allDeploymentConfigs(releases))
                              case _                  => EitherT.pure[Future, Result](Nil)
        yield
          Ok(whatsRunningWherePage(form.fill(filter), teamNames, digitalServices, sm2Profiles, config.maxMemoryAmount, filter.viewMode, versionResults, instanceResults))
      ).merge

case class WhatsRunningWhereFilter(
  serviceName   : Option[ServiceName]
, teamName      : Option[TeamName]
, digitalService: Option[DigitalService]
, sm2Profile    : Option[String]
, viewMode      : ViewMode
)

object WhatsRunningWhereFilter:
  lazy val form: Form[WhatsRunningWhereFilter] =
    Form:
      Forms.mapping(
        "serviceName"    -> Forms.optional(Forms.of[ServiceName])
      , "teamName"       -> Forms.optional(Forms.of[TeamName])
      , "digitalService" -> Forms.optional(Forms.of[DigitalService])
      , "sm2Profile"     -> Forms.optional(Forms.text)
      , "viewMode"       -> Forms.default(Forms.of[ViewMode], ViewMode.Versions),
      )(WhatsRunningWhereFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
