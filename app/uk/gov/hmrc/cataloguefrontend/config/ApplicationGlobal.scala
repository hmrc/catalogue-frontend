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

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api._
import play.api.mvc.Request
import play.twirl.api.Html
import uk.gov.hmrc.play.audit.filters.FrontendAuditFilter
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.frontend.bootstrap.DefaultFrontendGlobal
import uk.gov.hmrc.play.http.logging.filters.FrontendLoggingFilter

object ApplicationGlobal extends CatalogFrontendGlobal with RunMode {

  override val loggingFilter = CatLoggingFilter

  override def onStart(app: Application) {
    super.onStart(app)
  }

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit request: Request[_]): Html =
    views.html.error_template(pageTitle, heading, message)

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig("microservice.metrics")

}

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object CatLoggingFilter extends FrontendLoggingFilter {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object CatalogFrontendAuditFilter extends FrontendAuditFilter with RunMode with AppName {

  override lazy val maskedFormFields = Seq.empty

  override lazy val applicationPort = None

  override def controllerNeedsAuditing(controllerName: String) = false

  override def auditConnector: AuditConnector = ???
}
