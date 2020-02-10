/*
 * Copyright 2020 HM Revenue & Customs
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
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.WhatsRunningWherePage

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhatsRunningWhereController @Inject()(
  service: WhatsRunningWhereService,
  page: WhatsRunningWherePage,
  mcc: MessagesControllerComponents
)(implicit val ec: ExecutionContext)
    extends FrontendController(mcc) {

  import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WrWEnvironment.ordering

  def heritage: Action[AnyContent] =
    Action.async { implicit request =>
      for {
        form <- Future.successful(WhatsRunningWhereFilter.form.bindFromRequest)
        profile             = profileFrom(form)
        selectedProfileType = form.fold(_ => None, _.profileType).getOrElse(ProfileType.Team)
        (releases, profiles) <- (service.releases(profile), service.profiles).mapN { case (r, p) => (r, p) }
        environments = distinctEnvironments(releases)
        profileNames = profiles.filter(_.profileType == selectedProfileType).map(_.profileName).sorted
      } yield Ok(page(environments, releases, selectedProfileType, profileNames, form))
    }

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

  /*
   * todo - this is just duplicated at the moment.
   * Need to decide if its worth refactoring, or if we should think about merging, or removing
   * the heritage deployments stuff.
   */
  def ecs: Action[AnyContent] =
    Action.async { implicit request =>
      for {
        form <- Future.successful(WhatsRunningWhereFilter.form.bindFromRequest)
        profile             = profileFrom(form)
        selectedProfileType = form.fold(_ => None, _.profileType).getOrElse(ProfileType.Team)
        (releases, profiles) <- (service.ecsReleases(profile), service.profiles).mapN { case (r, p) => (r, p) }
        environments = distinctEnvironments(releases)
        profileNames = profiles.filter(_.profileType == selectedProfileType).map(_.profileName).sorted
      } yield Ok(page(environments, releases, selectedProfileType, profileNames, form))
    }

  def ecsAndHeritage: Action[AnyContent] =
    Action.async { implicit request =>
      for {
        form <- Future.successful(WhatsRunningWhereFilter.form.bindFromRequest)
        profile             = profileFrom(form)
        selectedProfileType = form.fold(_ => None, _.profileType).getOrElse(ProfileType.Team)
        (ecsReleases, profiles) <- (service.ecsReleases(profile), service.profiles).mapN { case (r, p) => (r, p) }
        heritageReleases        <- service.releases(profile)
        releases     = (ecsReleases ++ heritageReleases).sortBy(_.applicationName.asString)
        environments = distinctEnvironments(releases)
        profileNames = profiles.filter(_.profileType == selectedProfileType).map(_.profileName).sorted
      } yield Ok(page(environments, releases, selectedProfileType, profileNames, form))
    }
}

case class WhatsRunningWhereFilter(
  profileName: Option[ProfileName] = None,
  profileType: Option[ProfileType] = None,
  serviceName: Option[ServiceName] = None)

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
