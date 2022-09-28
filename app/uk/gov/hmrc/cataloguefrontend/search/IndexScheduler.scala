/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.search

import akka.actor.ActorSystem
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.cataloguefrontend.config.SearchConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class IndexScheduler @Inject()(
  searchIndex : SearchIndex,
  searchConfig: SearchConfig
)(implicit
  actorSystem         : ActorSystem,
  applicationLifecycle: ApplicationLifecycle,
  ec                  : ExecutionContext
) {
  private val logger = Logger(getClass)

  if (searchConfig.indexRebuildEnabled) {
    val cancellable =
      actorSystem.scheduler.scheduleAtFixedRate(1.second, searchConfig.indexRebuildInterval) {
        () => {
          logger.info("rebuilding search indexes")
          searchIndex.updateIndexes()
        }
      }

    applicationLifecycle.addStopHook(() => Future(cancellable.cancel()))
  } else
    logger.warn(s"The IndexScheduler has been disabled.")
}
