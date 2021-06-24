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

package uk.gov.hmrc.cataloguefrontend.shuttering

import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterStatus.{Shuttered, Unshuttered}

object ShutterEventsView {
  def reasonFor(status: ShutterStatus): String =
    statusReason(status).getOrElse("")

  private def statusReason(status: ShutterStatus): Option[String] =
    status match {
      case Shuttered(reason, _, _) => reason
      case Unshuttered             => None
    }
}
