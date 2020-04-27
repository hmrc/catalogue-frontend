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

package uk.gov.hmrc.cataloguefrontend.events

import akka.actor.{ActorSystem, Cancellable}
import javax.inject.{Inject, Singleton}
import play.api.Logger

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.ExecutionContext

@Singleton
class UpdateScheduler @Inject()(
  actorSystem     : ActorSystem,
  readModelService: ReadModelService
)(implicit val ec: ExecutionContext) {

  private val logger = Logger(getClass)

  val initialDelay: FiniteDuration = 1.second

  def startUpdatingEventsReadModel(interval: FiniteDuration): Cancellable = {
    logger.info(s"Initialising Event read model update every $interval")
    actorSystem.scheduler.schedule(initialDelay, interval) {
      readModelService.refreshEventsCache
    }
  }

  def startUpdatingUmpCacheReadModel(interval: FiniteDuration): Cancellable = {
    logger.info(s"Initialising UMP cache read model update every $interval")
    actorSystem.scheduler.schedule(initialDelay, interval) {
      readModelService.refreshUmpCache
    }
  }
}
