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

package uk.gov.hmrc.cataloguefrontend.deployments

import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, Version, given}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.{DeploymentConfigEvent, ServiceConfigsConnector}
import uk.gov.hmrc.cataloguefrontend.util.Parser
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{DeploymentTimelineEvent, ReleasesConnector}
import uk.gov.hmrc.http.HeaderCarrier

import java.time._
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeploymentGraphService @Inject() (
  releasesConnector      : ReleasesConnector,
  serviceConfigsConnector: ServiceConfigsConnector
)(using
  ExecutionContext
):

  def findEvents(
    service: ServiceName,
    start  : Instant,
    end    : Instant
  )(using HeaderCarrier): Future[Seq[DeploymentTimelineEvent]] =
    import DeploymentGraphService._
    for
      data                 <- releasesConnector.deploymentTimeline(service, start, end)
      dataWithPlaceholders =  data.toMap
                                .map: (env, data) =>
                                   env -> (if data.isEmpty then noEventsPlaceholder(env, start, end) else data)
      dataSeq              =  dataWithPlaceholders.values.flatten.toSeq.sortBy(_.env)
      deploymentConfigSeq  <- serviceConfigsConnector.deploymentEvents(service, start, end)
    yield updateTimelineEventsWithConfig(dataSeq, deploymentConfigSeq)

  private def updateTimelineEventsWithConfig(
    timelineEvents: Seq[DeploymentTimelineEvent],
    configEvents: Seq[DeploymentConfigEvent]
  ): Seq[DeploymentTimelineEvent] =
    val configEventMap: Map[String, DeploymentConfigEvent] =
      configEvents.map(event => event.deploymentId -> event).toMap

    timelineEvents.map: timelineEvent =>
      configEventMap
        .get(timelineEvent.deploymentId)
        .fold(timelineEvent): configEvent =>
          timelineEvent.copy(
            configChanged           = configEvent.configChanged,
            deploymentConfigChanged = configEvent.deploymentConfigChanged,
            configId                = configEvent.configId
          )

object DeploymentGraphService:

  val notDeployedMessage = "Not Deployed" // Note this version also exists in releases-api for un-deployment events

  // generates a placeholder timeline event so that the timeline still shows the environment but with nothing deployed in it
  def noEventsPlaceholder(env: String, start: Instant, end: Instant): Seq[DeploymentTimelineEvent] =
    Parser[Environment]
      .parse(env)
      .fold(
        _ => Seq.empty[DeploymentTimelineEvent]
      , e => Seq(DeploymentTimelineEvent(e, Version(notDeployedMessage), "", "", start.plusSeconds(1), end.minusSeconds(1)))
      )
