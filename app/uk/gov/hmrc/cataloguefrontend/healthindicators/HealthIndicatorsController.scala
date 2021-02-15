/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.healthindicators

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.{HealthIndicatorsPage, error_404_template}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class HealthIndicatorsController @Inject()(
  healthIndicatorsConnector: HealthIndicatorsConnector,
                                            mcc    : MessagesControllerComponents
                                          )(implicit val ec: ExecutionContext)
  extends FrontendController(mcc){

     def indicatorsForRepo(repo: String): Action[AnyContent] = {

       Action.async { implicit request =>
         for {
           repositoryRating <- healthIndicatorsConnector.getHealthIndicators(repo)
         } yield repositoryRating match {
           case Some(_) => Ok(HealthIndicatorsPage(repositoryRating.get))
           case None => NotFound(error_404_template())
         }
       }
     }
}
