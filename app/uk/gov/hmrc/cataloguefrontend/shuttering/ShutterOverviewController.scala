/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.actions.VerifySignInStatus
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.shuttering._

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import cats.implicits._

@Singleton
class ShutterOverviewController @Inject()(
  mcc                      : MessagesControllerComponents,
  verifySignInStatus       : VerifySignInStatus,
  shutterOverviewPage         : ShutterOverviewPage,
  frontendRoutesWarningPage: FrontendRouteWarningsPage,
  shutterService           : ShutterService
)(implicit val ec: ExecutionContext)
    extends FrontendController(mcc) {


  def allStates: Action[AnyContent] =
    allStatesForEnv(envParam = uk.gov.hmrc.cataloguefrontend.shuttering.Environment.Production.asString)

  def allStatesForEnv(envParam: String): Action[AnyContent] =
    verifySignInStatus.async { implicit request =>
      val env = Environment.parse(envParam).getOrElse(Environment.Production)
      for {
        currentState <- shutterService
                         .findCurrentStates(env)
                         .recover {
                           case NonFatal(ex) =>
                             Logger.error(s"Could not retrieve currentState: ${ex.getMessage}", ex)
                             Seq.empty
                         }
        page = shutterOverviewPage(currentState, env, request.isSignedIn)
      } yield Ok(page)
    }

  def frontendRouteWarnings(envParam: String, serviceName: String): Action[AnyContent] =
    Action.async { implicit request =>
      val env = Environment.parse(envParam).getOrElse(Environment.Production)
      for {
        envsAndWarnings <- Environment.values.map(env => shutterService.frontendRouteWarnings(env, serviceName).recover {
          case NonFatal(ex) =>
            Logger.error(
              s"Could not retrieve frontend route warnings for service '$serviceName' in env: '$envParam': ${ex.getMessage}",
              ex)
            Seq.empty
        }.map(ws => (env, ws))).sequence
        page = frontendRoutesWarningPage(envsAndWarnings.toMap, env, serviceName)
      } yield Ok(page)
    }
}
