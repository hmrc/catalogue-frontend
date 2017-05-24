/*
 * Copyright 2017 HM Revenue & Customs
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
import play.api.inject.ApplicationLifecycle
import play.api.mvc.Request
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.events.UpdateScheduler
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.play.audit.filters.FrontendAuditFilter
import uk.gov.hmrc.play.audit.http.config.LoadAuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.filters.MicroserviceFilterSupport
import uk.gov.hmrc.play.frontend.bootstrap.DefaultFrontendGlobal
import uk.gov.hmrc.play.http.logging.filters.FrontendLoggingFilter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._


object ApplicationGlobal extends DefaultFrontendGlobal {
  override val auditConnector = FrontendAuditConnector
  override val frontendAuditFilter = AuditFilter
  override val loggingFilter = CatLoggingFilter

  val eventReloadIntervalKey = "event.reload.interval"
  val umpCacheReloadIntervalKey = "ump.cache.reload.interval"


  override def onStart(app: Application) {
    Logger.info(s"Starting frontend : $appName : in mode : ${app.mode}")
    Logger.debug("[Catalogue-frontend] - Starting... ")
    super.onStart(app)
    ApplicationCrypto.verifyConfiguration()

    scheduleEventsReloadSchedule(app)
    scheduleUmpCacheReloadSchedule(app)
  }


  private def scheduleEventsReloadSchedule(app: Application) = {
    val maybeReloadInterval = app.configuration.getInt(eventReloadIntervalKey)

    maybeReloadInterval.fold {
      Logger.warn(s"$eventReloadIntervalKey is missing. Event cache reload will be disabled")
    } { reloadInterval =>
      Logger.warn(s"EventReloadInterval set to $reloadInterval seconds")
      val cancellable = UpdateScheduler.startUpdatingEventsReadModel(reloadInterval seconds)
      app.injector.instanceOf[ApplicationLifecycle].addStopHook(() => Future(cancellable.cancel()))
    }
  }

  private def scheduleUmpCacheReloadSchedule(app: Application) = {
    val maybeUmpCacheReloadInterval = app.configuration.getInt(umpCacheReloadIntervalKey)

    maybeUmpCacheReloadInterval.fold {
      Logger.warn(s"$umpCacheReloadIntervalKey is missing. Ump cache reload will be disabled")
    } { reloadInterval =>
      Logger.warn(s"UMP cache reload interval set to $reloadInterval seconds")
      val cancellable = UpdateScheduler.startUpdatingUmpCacheReadModel(reloadInterval seconds)
      app.injector.instanceOf[ApplicationLifecycle].addStopHook(() => Future(cancellable.cancel()))
    }
  }

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit request: Request[_]): Html =
    views.html.error_template(pageTitle, heading, message)


  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig("microservice.metrics")

}

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object CatLoggingFilter extends FrontendLoggingFilter with MicroserviceFilterSupport  {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object AuditFilter extends FrontendAuditFilter with MicroserviceFilterSupport with RunMode with AppName {

  override lazy val maskedFormFields = Seq("password")

  override lazy val applicationPort = None

  override lazy val auditConnector = FrontendAuditConnector

  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object FrontendAuditConnector extends AuditConnector with AppName {
  override lazy val auditingConfig = LoadAuditingConfig(s"auditing")
}
