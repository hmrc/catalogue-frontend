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

package uk.gov.hmrc.cataloguefrontend.service

import javax.inject.{Inject, Named, Singleton}
import play.api._
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.cataloguefrontend.events.UpdateScheduler

import scala.concurrent.Future
import scala.concurrent.duration._

@Singleton
class EventsReloadScheduler @Inject()(
  appLifecycle: ApplicationLifecycle,
  configuration: Configuration,
  environment: Environment,
  @Named("appName") appName: String,
  updateScheduler: UpdateScheduler) {

  private val logger = Logger(getClass)

  val eventReloadIntervalKey    = "event.reload.interval"
  val umpCacheReloadIntervalKey = "ump.cache.reload.interval"

  logger.info(s"Starting : $appName : in mode : ${environment.mode}")
  logger.debug("[Catalogue-frontend] - Starting... ")

  scheduleEventsReloadSchedule(appLifecycle, configuration)
  scheduleUmpCacheReloadSchedule(appLifecycle, configuration)

  private def scheduleEventsReloadSchedule(appLifecycle: ApplicationLifecycle, configuration: Configuration): Unit = {
    val reloadInterval = configuration.getMillis(eventReloadIntervalKey).millis
    val cancellable    = updateScheduler.startUpdatingEventsReadModel(reloadInterval)
    appLifecycle.addStopHook(() => Future.successful(cancellable.cancel()))
  }

  private def scheduleUmpCacheReloadSchedule(appLifecycle: ApplicationLifecycle, configuration: Configuration): Unit = {
    lazy val umpCacheReloadInterval = configuration.getMillis(umpCacheReloadIntervalKey).milliseconds

    logger.info(s"UMP cache reload interval set to $umpCacheReloadInterval")
    val cancellable = updateScheduler.startUpdatingUmpCacheReadModel(umpCacheReloadInterval)
    appLifecycle.addStopHook(() => Future.successful(cancellable.cancel()))
  }
}
