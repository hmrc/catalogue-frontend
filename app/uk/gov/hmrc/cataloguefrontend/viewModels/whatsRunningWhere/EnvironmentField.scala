/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.viewModels.whatsRunningWhere

import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereVersion

trait EnvironmentField {
  val env: Environment
}

case class EnvironmentWithoutVersion(env: Environment) extends EnvironmentField
case class EnvironmentWithVersion(env: Environment, version: WhatsRunningWhereVersion) extends EnvironmentField {
  val isPatchedVersion: Boolean = version.versionNumber.asVersion.patch > 0
}
