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
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.config.CatalogueConfig
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.ServiceName
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, IAAction, Predicate, Resource, Retrieval}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.shuttering._

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

@Singleton
class ShutterOverviewController @Inject() (
  override val mcc         : MessagesControllerComponents,
  shutterOverviewPage      : ShutterOverviewPage,
  frontendRoutesWarningPage: FrontendRouteWarningsPage,
  shutterService           : ShutterService,
  catalogueConfig          : CatalogueConfig,
  override val auth        : FrontendAuthComponents
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  private val logger = Logger(getClass)

  def allStates(shutterType: ShutterType): Action[AnyContent] =
    allStatesForEnv(
      shutterType = shutterType,
      env         = Environment.Production
    )

  def allStatesForEnv(shutterType: ShutterType, env: Environment): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        envAndCurrentStates <- Environment.values.traverse { env =>
                                 shutterService
                                   .findCurrentStates(shutterType, env)
                                   .recover {
                                     case NonFatal(ex) =>
                                       logger.error(s"Could not retrieve currentState: ${ex.getMessage}", ex)
                                       Seq.empty
                                   }
                                   .map(ws => (env, ws))
                               }
        hasGlobalPerm  <-  auth
                            .verify(
                              Retrieval.hasPredicate(Predicate.Permission(Resource.from("shutter-api", "mdtp"), IAAction("SHUTTER")))
                            ).map(_.exists(_ == true))
        killSwitchLink =  if (hasGlobalPerm) Some(catalogueConfig.killSwitchLink(shutterType.asString, env.asString)) else None
        page           =  shutterOverviewPage(envAndCurrentStates.toMap, shutterType, env, killSwitchLink)
      } yield Ok(page)
    }

  def frontendRouteWarnings(env: Environment, serviceName: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        envsAndWarnings <- Environment.values.traverse { env =>
                             shutterService
                               .frontendRouteWarnings(env, ServiceName(serviceName))
                               .recover {
                                 case NonFatal(ex) =>
                                   logger.error(s"Could not retrieve frontend route warnings for service '$serviceName' in env: '${env.asString}': ${ex.getMessage}", ex)
                                   Seq.empty
                               }
                               .map(ws => (env, ws))
                           }
        page = frontendRoutesWarningPage(envsAndWarnings.toMap, env, serviceName)
      } yield Ok(page)
    }
}
