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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.WhatsRunningWherePage

import scala.concurrent.ExecutionContext

@Singleton
class WhatsRunningWhereController @Inject()(
    releasesConnector: ReleasesConnector
  , page: WhatsRunningWherePage
  , mcc: MessagesControllerComponents
  )(implicit val ec: ExecutionContext
  ) extends FrontendController(mcc){

  def landing(profileName: Option[String]): Action[AnyContent] =
    Action.async { implicit request =>

      for {
        releases <- releasesConnector.releases(profileName.map(ProfileName.apply))
      } yield {
        val environments = releases.flatMap(_.versions.map(_.environment)).distinct
        Ok(page(environments, releases))
      }
    }
}
