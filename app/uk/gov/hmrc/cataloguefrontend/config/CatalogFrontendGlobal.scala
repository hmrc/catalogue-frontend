/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.config

import play.api._
import play.api.mvc._
import uk.gov.hmrc.play.frontend.bootstrap.Routing.RemovingOfTrailingSlashes
import uk.gov.hmrc.play.frontend.bootstrap.ShowErrorPage
import uk.gov.hmrc.play.graphite.GraphiteConfig


abstract class CatalogFrontendGlobal
  extends GlobalSettings
  with FrontendFilters
  with GraphiteConfig
  with RemovingOfTrailingSlashes
  with ShowErrorPage {

  lazy val appName = Play.current.configuration.getString("appName").getOrElse("APP NAME NOT SET")

  override def onStart(app: Application) {
    Logger.info(s"Starting frontend : $appName : in mode : ${app.mode}")
    super.onStart(app)
  }

  override def doFilter(a: EssentialAction): EssentialAction =
    Filters(super.doFilter(a), frontendFilters: _* )

}
