/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import play.api._
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.cataloguefrontend.events.UpdateScheduler
import uk.gov.hmrc.play.bootstrap.config.AppName

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

@Singleton
class EventsReloadScheduler @Inject()(
  appLifecycle: ApplicationLifecycle,
  val configuration: Configuration,
  environment: Environment,
  updateScheduler: UpdateScheduler) {

  val eventReloadIntervalKey    = "event.reload.interval"
  val umpCacheReloadIntervalKey = "ump.cache.reload.interval"

  Logger.info(s"Starting : ${environment.rootPath} : in mode : ${environment.mode}")
  Logger.debug("[Catalogue-frontend] - Starting... ")

  scheduleEventsReloadSchedule(appLifecycle, configuration)
  scheduleUmpCacheReloadSchedule(appLifecycle, configuration)

  private def scheduleEventsReloadSchedule(appLifecycle: ApplicationLifecycle, configuration: Configuration) = {

    lazy val maybeReloadInterval = configuration.getMilliseconds(eventReloadIntervalKey).map(_.millisecond)

    maybeReloadInterval.fold {
      Logger.warn(s"$eventReloadIntervalKey is missing. Event cache reload will be disabled")
    } { reloadInterval =>
      Logger.warn(s"EventReloadInterval set to ${reloadInterval.toSeconds} seconds")
      val cancellable = updateScheduler.startUpdatingEventsReadModel(reloadInterval)
      appLifecycle.addStopHook(() => Future(cancellable.cancel()))
    }
  }

  private def scheduleUmpCacheReloadSchedule(appLifecycle: ApplicationLifecycle, configuration: Configuration) = {
    lazy val maybeUmpCacheReloadInterval = configuration.getMilliseconds(umpCacheReloadIntervalKey).map(_.milliseconds)

    maybeUmpCacheReloadInterval.fold {
      Logger.warn(s"$umpCacheReloadIntervalKey is missing. Ump cache reload will be disabled")
    } { reloadInterval =>
      Logger.warn(s"UMP cache reload interval set to ${reloadInterval.toSeconds} seconds")
      val cancellable = updateScheduler.startUpdatingUmpCacheReadModel(reloadInterval)
      appLifecycle.addStopHook(() => Future(cancellable.cancel()))
    }
  }

}
