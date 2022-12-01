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

package uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.model.Environment


case class StatusCheck(
  evidence: Option[String]
)

object StatusCheck {
  val reads: Reads[StatusCheck] =
    (__ \ "evidence").readNullable[String].map(StatusCheck(_))
}

case class Dashboard(
  kibana  : StatusCheck
, grafana : StatusCheck
)

object Dashboard {
  val reads: Reads[Dashboard] = {
    implicit val scReads: Reads[StatusCheck] = StatusCheck.reads
    ( (__ \ "kibana").read[StatusCheck]
    ~ (__ \ "grafana").read[StatusCheck]
    ) (Dashboard.apply _)
  }
}

case class ServiceCommissioningStatus(
  serviceName      : String
, hasRepo          : StatusCheck
, hasSMConfig      : StatusCheck
, hasFrontendRoutes: Map[Environment, StatusCheck]
, hasAppConfigBase : StatusCheck
, hasAppConfigEnv  : Map[Environment, StatusCheck]
, isDeployed       : Map[Environment, StatusCheck]
, hasDashboards    : Dashboard
, hasBuildJobs     : StatusCheck
, hasAlerts        : StatusCheck
)

object ServiceCommissioningStatus {
  private implicit val scReads : Reads[StatusCheck]                   = StatusCheck.reads
  private implicit val dsReads : Reads[Dashboard]                     = Dashboard.reads
  private implicit val mapReads: Reads[Map[Environment, StatusCheck]] =
    Reads
      .of[Map[String, StatusCheck]]
      .map(
        _.map { case (k, v) => (Environment.parse(k).getOrElse(sys.error("Invalid Environment")), v) }
      )

  val reads: Reads[ServiceCommissioningStatus] =
    ( (__ \ "serviceName"      ).read[String]
    ~ (__ \ "hasRepo"          ).read[StatusCheck]
    ~ (__ \ "hasSMConfig"      ).read[StatusCheck]
    ~ (__ \ "hasFrontendRoutes").read[Map[Environment, StatusCheck]]
    ~ (__ \ "hasAppConfigBase" ).read[StatusCheck]
    ~ (__ \ "hasAppConfigEnv"  ).read[Map[Environment, StatusCheck]]
    ~ (__ \ "isDeployedIn"     ).read[Map[Environment, StatusCheck]]
    ~ (__ \ "hasDashboards"    ).read[Dashboard]
    ~ (__ \ "hasBuildJobs"     ).read[StatusCheck]
    ~ (__ \ "hasAlerts"        ).read[StatusCheck]
    ) (ServiceCommissioningStatus.apply _)
}
