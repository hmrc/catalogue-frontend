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

import cats.implicits._
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.i18n.Messages.implicitMessagesProviderToMessages
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, MessagesRequest}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.views.html.whatsrunningwhere.WhatsRunningWherePage
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class WhatsRunningWhereController @Inject() (
  service           : WhatsRunningWhereService,
  page              : WhatsRunningWherePage,
  config            : WhatsRunningWhereServiceConfig,
  override val mcc  : MessagesControllerComponents,
  override val auth : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  private def profileFrom(form: Form[WhatsRunningWhereFilter]): Option[Profile] =
    form.fold(
      _ => None,
      f =>
        for
          profileType <- f.profileType
          profileName <- f.profileName
        yield Profile(profileType, profileName)
    )

  private def distinctEnvironments(releases: Seq[WhatsRunningWhere]): Seq[Environment] =
    releases.flatMap(_.versions.map(_.environment)).distinct.sorted

  def releases(): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      val form                  = WhatsRunningWhereFilter.form.bindFromRequest()
      val profile               = profileFrom(form)
      val selectedProfileType   = form.fold(_ => None, _.profileType).getOrElse(ProfileType.Team)
      val selectedViewMode      = form.fold(_ => None, _.viewMode).getOrElse(ViewMode.Versions)

      selectedViewMode match
        case ViewMode.Instances => instancesPage(form, profile, selectedProfileType, selectedViewMode).map(page => Ok(page))
        case ViewMode.Versions  => versionsPage (form, profile, selectedProfileType, selectedViewMode).map(page => Ok(page))
    }


  private def versionsPage(
    form               : Form[WhatsRunningWhereFilter],
    profile            : Option[Profile],
    selectedProfileType: ProfileType,
    selectedViewMode   : ViewMode,
  )(using
    request            : MessagesRequest[AnyContent
  ]) =
    for
      profiles     <- service.profiles()
      releases     <- service.releasesForProfile(profile).map(_.sortBy(_.serviceName.asString))
      environments =  distinctEnvironments(releases)
      profileNames =  profiles.filter(_.profileType == selectedProfileType).map(_.profileName).sorted
    yield page(environments, releases, selectedProfileType, profileNames, form, Seq.empty, config.maxMemoryAmount, selectedViewMode)


  private def instancesPage(
    form               : Form[WhatsRunningWhereFilter],
    profile            : Option[Profile],
    selectedProfileType: ProfileType,
    selectedViewMode   : ViewMode
  )(using
    request            : MessagesRequest[AnyContent]
  ) =
    for
      profiles           <- service.profiles()
      releases           <- service.releasesForProfile(profile).map(_.sortBy(_.serviceName.asString))
      environments       =  distinctEnvironments(releases)
      serviceDeployments <- service.allDeploymentConfigs(releases)
      profileNames       =  profiles.filter(_.profileType == selectedProfileType).map(_.profileName).sorted
    yield page(environments, Seq.empty, selectedProfileType, profileNames, form, serviceDeployments.sortBy(_.serviceName), config.maxMemoryAmount, selectedViewMode)

end WhatsRunningWhereController

case class WhatsRunningWhereFilter(
  profileName: Option[ProfileName] = None,
  profileType: Option[ProfileType] = None,
  serviceName: Option[ServiceName] = None,
  viewMode:    Option[ViewMode]    = None
)

object WhatsRunningWhereFilter:
  private def filterEmptyString(x: Option[String]) =
    x.filter(_.trim.nonEmpty)

  lazy val form: Form[WhatsRunningWhereFilter] =
    Form(
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
                            ),
        "view_mode"    -> optional(text)
                          .transform[Option[ViewMode]](
                            _.flatMap(s => ViewMode.parse(s).toOption),
                            _.map(_.asString)
                          )
      )(WhatsRunningWhereFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
    )
