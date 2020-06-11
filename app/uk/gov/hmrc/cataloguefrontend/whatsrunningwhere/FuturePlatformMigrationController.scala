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

import javax.inject.{Inject, Singleton}
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.whatsrunningwhere.FuturePlatformMigrationPage

import scala.collection.immutable.SortedMap
import scala.concurrent.ExecutionContext

@Singleton
class FuturePlatformMigrationController @Inject()(
  cc                         : MessagesControllerComponents,
  releasesConnector          : ReleasesConnector,
  futurePlatformMigrationPage: FuturePlatformMigrationPage,
)(implicit val ec: ExecutionContext
) extends FrontendController(cc) {

  def futurePlatformMigration =
    Action.async { implicit request =>
      for {
        servicePlatformMappings <- releasesConnector.getServicePlatformMappings
        serviceData             =  sortByKeys(
                                     servicePlatformMappings
                                       .groupBy(_.serviceName)
                                       .mapValues(_.map(v => (v.environment, v.platform)).toMap)
                                   )
        platformCount           =  sortByKeys(
                                     servicePlatformMappings
                                       .groupBy(_.environment)
                                       .mapValues(_.groupBy(_.platform).mapValues(_.size))
                                   )
      } yield Ok(futurePlatformMigrationPage(serviceData, platformCount))
    }

  def sortByKeys[A : Ordering, B](m: Map[A, B]): Map[A, B] =
    SortedMap[A, B]() ++ m
}
