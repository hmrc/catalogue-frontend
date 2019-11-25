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
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
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

  def landing: Action[AnyContent] =
    Action.async { implicit request =>

      val form = WhatsRunningWhereFilter.form.bindFromRequest()
      val profileName = form.fold(_ => None, filter => filter.profileName)

      for {
        releases <- releasesConnector.releases(profileName)
      } yield {
        val environments = releases.flatMap(_.versions.map(_.environment)).distinct.sorted
        Ok(page(environments, releases, form))
      }
    }
}

case class WhatsRunningWhereFilter(profileName: Option[ProfileName] = None,
                                   applicationName: Option[ApplicationName] = None)

object WhatsRunningWhereFilter {
  private def filterEmptyString(x: Option[String]) = x.filter(_.trim.nonEmpty)

  lazy val form = Form(
    mapping(
      "profile_name" -> optional(text).transform[Option[ProfileName]](filterEmptyString(_).map(ProfileName), _.map(_.asString)),
      "application_name" -> optional(text).transform[Option[ApplicationName]](filterEmptyString(_).map(ApplicationName), _.map(_.asString))
    )(WhatsRunningWhereFilter.apply)(WhatsRunningWhereFilter.unapply)
  )
}
