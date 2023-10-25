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

package uk.gov.hmrc.cataloguefrontend.util

import play.api.Configuration

import uk.gov.hmrc.cataloguefrontend.connector.Link
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.connector.ServiceMetricsConnector

import java.security.MessageDigest
import javax.inject.{Inject, Singleton}

@Singleton
class TelemetryLinks @Inject()(configuration: Configuration) {

  private val grafanaDashboardTemplate           = configuration.get[String]("telemetry.templates.metrics")
  private val kibanaDashboardTemplate            = configuration.get[String]("telemetry.templates.logs.dashBoard")
  private val kibanaDeploymentLogsTemplate       = configuration.get[String]("telemetry.templates.logs.deploymentLogs")
  private val telemetryLogsDiscoverLinkTemplates = configuration.get[Map[String, String]]("telemetry.templates.logs.discover")

  // Same as https://github.com/hmrc/grafana-dashboards/blob/main/src/main/scala/uk/gov/hmrc/grafanadashboards/domain/dashboard/DashboardBuilder.scala#L49-L57
  private def toDashBoardUid(name: String): String =
    if (name.length > 40)
      name.take(8) + MessageDigest
                      .getInstance("MD5")
                      .digest(name.getBytes)
                      .map("%02x".format(_))
                      .mkString
    else
      name

  def grafanaDashboard(env: Environment, serviceName: String) = Link(
    name        = "Grafana Dashboard"
  , displayName = "Grafana Dashboard"
  , url        =  grafanaDashboardTemplate
                    .replace(s"$${env}",     UrlUtils.encodePathParam(env.asString))
                    .replace(s"$${service}", UrlUtils.encodePathParam(toDashBoardUid(serviceName)))
  )

  def kibanaDashboard(env: Environment, serviceName: String) = Link(
    name        = "Kibana Dashboard"
  , displayName = "Kibana Dashboard"
  , url        =  kibanaDashboardTemplate
                    .replace(s"$${env}",     UrlUtils.encodePathParam(env.asString))
                    .replace(s"$${service}", UrlUtils.encodePathParam(serviceName))
  )

  def kibanaDeploymentLogs(env: Environment, serviceName: String) = Link(
    name        = "Deployment Logs"
  , displayName = "Deployment Logs"
  , url        =  kibanaDeploymentLogsTemplate
                    .replace(s"$${env}",     UrlUtils.encodePathParam(env.asString))
                    .replace(s"$${service}", UrlUtils.encodePathParam(serviceName))
  )

  def kibanaNonPerformantQueries(
    env        : Environment,
    serviceName: String,
    nonPerformantQueries: Seq[ServiceMetricsConnector.NonPerformantQueries] = Seq.empty
  ): Seq[Link] = {
    telemetryLogsDiscoverLinkTemplates.toSeq.map{ case (name, linkTemplate) =>
      val `class` = nonPerformantQueries.collectFirst{ 
        case npq if (npq.service == serviceName &&
          npq.queryTypes.exists(_.contains(name)) &&
          npq.environment == env) => "glyphicon glyphicon-exclamation-sign text-danger"
      }
      val url = linkTemplate.replace(s"$${env}", UrlUtils.encodePathParam(env.asString))
      .replace(s"$${service}", UrlUtils.encodePathParam(serviceName))
      Link(name, name, url, `class`)
    }
  }
    
}
