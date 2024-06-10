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

import uk.gov.hmrc.cataloguefrontend.connector.model.Version
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.{DeploymentConfigEvent, ServiceConfigsConnector}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{DeploymentTimelineEvent, ReleasesConnector}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse.Upstream4xxResponse

import java.time._
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeploymentGraphService @Inject() (releasesConnector: ReleasesConnector, serviceConfigsConnector: ServiceConfigsConnector)(implicit ec: ExecutionContext) {

  def findEvents(service: String, start: Instant, end: Instant): Future[Seq[DeploymentTimelineEvent]] = {
    import DeploymentGraphService._
    implicit val hc: HeaderCarrier = HeaderCarrier()

    for {
      data                 <- releasesConnector.deploymentTimeline(service, start, end).recover { case Upstream4xxResponse(_) => Map.empty }
      dataWithPlaceholders =  data.toSeq.map {
                                case (env, Nil)  => env -> noEventsPlaceholder(env, start,end)
                                case (env, data) => env -> data
                              }.toMap
      dataSeq              =  dataWithPlaceholders.values.flatten.toSeq.sortBy(_.env)
      deploymentConfigSeq  <- serviceConfigsConnector.deploymentEvents(service, dataSeq.map(_.deploymentId))
    } yield updateTimelineEventsWithConfig(dataSeq, deploymentConfigSeq)

  }

  private def updateTimelineEventsWithConfig(
    timelineEvents: Seq[DeploymentTimelineEvent],
    configEvents: Seq[DeploymentConfigEvent]
  ): Seq[DeploymentTimelineEvent] = {
    val configEventMap: Map[String, DeploymentConfigEvent] = configEvents.map(event => event.deploymentId -> event).toMap

    timelineEvents.map { timelineEvent =>
      configEventMap.get(timelineEvent.deploymentId) match {
        case Some(configEvent) =>
          timelineEvent.copy(
            configChanged = Some(configEvent.configChanged),
            configId = Some(configEvent.configId)
          )
        case None =>
          timelineEvent
      }
    }
  }
}

object DeploymentGraphService {

  val notDeployedMessage = "Not Deployed"

  // generates a placeholder timeline event so that the timeline still shows the environment but with nothing deployed in it
  def noEventsPlaceholder(env: String, start: Instant, end: Instant): Seq[DeploymentTimelineEvent] =
    Environment.parse(env).fold(Seq.empty[DeploymentTimelineEvent])(e => Seq(DeploymentTimelineEvent(e, Version(notDeployedMessage), "", "", start.plusSeconds(1), end.minusSeconds(1))))

}
