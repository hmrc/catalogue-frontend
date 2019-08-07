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

package uk.gov.hmrc.cataloguefrontend
import javax.inject.{Inject, Singleton}
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.cataloguefrontend.connector.model.{ JDKVersion, JDKVersionFormats}
import uk.gov.hmrc.cataloguefrontend.service.DependenciesService
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import views.html.{JdkAcrossEnvironmentsPage, JdkVersionPage}
import uk.gov.hmrc.cataloguefrontend.connector.SlugInfoFlag

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JDKVersionController @Inject()(
                                      cc: ControllerComponents,
                                      dependenciesService: DependenciesService,
                                      jdkPage: JdkVersionPage,
                                      jdkCountsPage: JdkAcrossEnvironmentsPage
                                      )(
  implicit val ec: ExecutionContext)
    extends BackendController(cc) {

  def findLatestVersions(flag: String) = Action.async { implicit request =>
    implicit val r = JDKVersionFormats.jdkFormat

    for {
        flag        <- Future.successful(SlugInfoFlag.parse(flag.toLowerCase).getOrElse(SlugInfoFlag.Latest))
        jdkVersions <- dependenciesService.getJDKVersions(flag)
    } yield Ok(jdkPage(jdkVersions.sortBy(byJDKVersion), SlugInfoFlag.values, flag))
  }

  def compareAllEnvironments() = Action.async { implicit request =>
    //implicit val r = JDKUsageByEnvFormat.jdkUsageByEnvFormat

    for {
       envs      <- Future.sequence(SlugInfoFlag.values.map(dependenciesService.getJDKCountsForEnv))
       jdks      =  envs.flatMap(_.usage.keys).distinct.sortBy(byJDKVersion)
    } yield Ok(jdkCountsPage(envs, jdks))
  }

  private def byJDKVersion(v: JDKVersion) = v.version.replaceAll("\\D", "").toInt

}
