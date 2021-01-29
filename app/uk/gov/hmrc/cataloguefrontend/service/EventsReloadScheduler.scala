/*
 * Copyright 2021 HM Revenue & Customs
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

import akka.actor.Cancellable
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
  updateScheduler: UpdateScheduler
) {

  private val logger = Logger(getClass)

  schedule("event.reload")(updateScheduler.startUpdatingEventsReadModel)

  schedule("ump.cache.reload")(updateScheduler.startUpdatingUmpCacheReadModel)

  private def schedule(schedulerKey: String)(f: (FiniteDuration) => Cancellable): Unit =
    if (configuration.get[Boolean](s"$schedulerKey.enabled")) {
      val interval = configuration.getMillis(s"$schedulerKey.interval").millis
      logger.info(s"Enabling $schedulerKey scheduler, running every $interval")
      val cancellable = f(interval)
      appLifecycle.addStopHook(() => Future.successful(cancellable.cancel()))
    } else
      logger.info(s"$schedulerKey scheduler is DISABLED. to enable, configure configure $schedulerKey.enabled=true in config.")
}
