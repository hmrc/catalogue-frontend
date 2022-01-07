/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.implicits._
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.whatsrunningwhere.WhatsRunningWherePage

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhatsRunningWhereController @Inject() (
  service: WhatsRunningWhereService,
  page: WhatsRunningWherePage,
  mcc: MessagesControllerComponents
)(implicit val ec: ExecutionContext)
    extends FrontendController(mcc) {

  private def profileFrom(form: Form[WhatsRunningWhereFilter]): Option[Profile] =
    form.fold(
      _ => None,
      f =>
        for {
          profileType <- f.profileType
          profileName <- f.profileName
        } yield Profile(profileType, profileName)
    )

  private def distinctEnvironments(releases: Seq[WhatsRunningWhere]) =
    releases.flatMap(_.versions.map(_.environment)).distinct.sorted

  def releases(showDiff: Boolean): Action[AnyContent] =
    Action.async { implicit request =>
      for {
        form <- Future.successful(WhatsRunningWhereFilter.form.bindFromRequest)
        profile             = profileFrom(form)
        selectedProfileType = form.fold(_ => None, _.profileType).getOrElse(ProfileType.Team)
        (releases, profiles) <- (service.releasesForProfile(profile).map(_.sortBy(_.applicationName.asString)), service.profiles).mapN((r, p) => (r, p))
        environments = distinctEnvironments(releases)
        profileNames = profiles.filter(_.profileType == selectedProfileType).map(_.profileName).sorted
      } yield Ok(page(environments, releases, selectedProfileType, profileNames, form, showDiff))
    }
}

object WhatsRunningWhereController {
  def matchesProduction(wrw: WhatsRunningWhere, prodVersion: WhatsRunningWhereVersion, comparedEnv: Environment): Boolean =
    wrw.versions
      .find(_.environment == comparedEnv)
      .map(_.versionNumber)
      .contains(prodVersion.versionNumber)

}
case class WhatsRunningWhereFilter(
  profileName: Option[ProfileName] = None,
  profileType: Option[ProfileType] = None,
  serviceName: Option[ServiceName] = None
)

object WhatsRunningWhereFilter {
  private def filterEmptyString(x: Option[String]) = x.filter(_.trim.nonEmpty)

  lazy val form = Form(
    mapping(
      "profile_name" -> optional(text)
        .transform[Option[ProfileName]](
          _.map(ProfileName.apply),
          _.map(_.asString)
        ),
      "profile_type" -> optional(text)
        .transform[Option[ProfileType]](
          _.flatMap(s => ProfileType.parse(s).toOption),
          _.map(_.asString)
        ),
      "service_name" -> optional(text)
        .transform[Option[ServiceName]](
          filterEmptyString(_).map(ServiceName.apply),
          _.map(_.asString)
        )
    )(WhatsRunningWhereFilter.apply)(WhatsRunningWhereFilter.unapply)
  )
}
