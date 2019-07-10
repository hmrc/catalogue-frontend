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

package uk.gov.hmrc.cataloguefrontend.shuttering

import java.time.LocalDateTime

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterService @Inject()(shutterConnector: ShutterConnector)(implicit val ec: ExecutionContext) {

  def getShutterStates(implicit hc: HeaderCarrier): Future[Seq[ShutterState]] =
    shutterConnector.shutterStates

  def updateShutterStatus(serviceName: String, env: Environment, status: ShutterStatus)(implicit hc: HeaderCarrier): Future[Unit] =
    shutterConnector.updateShutterStatus(serviceName, env, status)

  def findCurrentState(env: Environment)(implicit hc: HeaderCarrier): Future[Seq[ShutterEvent]] =
    for {
      events <- shutterConnector.latestShutterEvents(env)
      sorted = events.sortWith { case (l, r) => l.ssData.status == ShutterStatus.Shuttered ||
                                                l.ssData.serviceName < r.ssData.serviceName
                               }
    } yield sorted
}
