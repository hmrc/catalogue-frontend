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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.model

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.Environment

case class ServiceDeploymentInfra
(
  name       : String,
  environment: String,
  slots      : Int = 0,
  instances  : Int = 0,
)

object ServiceDeploymentInfra {
  val reads: Reads[ServiceDeploymentInfra] = {
    ((__ \ "name").read[String]
    ~ (__ \ "environment").read[String]
    ~ (__ \ "slots").read[Int]
    ~ (__ \ "instances").read[Int]
      )(ServiceDeploymentInfra.apply _)
    }
}

case class ServiceDeploymentInfraSummary
(
  serviceDetails: ServiceDeploymentInfra,
  cost          : Double
)
