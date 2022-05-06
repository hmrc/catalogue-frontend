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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse.Upstream4xxResponse

import java.time._
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeploymentGraphService @Inject() (releasesConnector: ReleasesConnector)(implicit ec: ExecutionContext) {

  // search 60 days either side of the time-range, this allows us to find deployments that intersect the time-range
  val margin = Duration.ofDays(90)

  def findEvents(service: String, start: Instant, end: Instant): Future[Seq[DeploymentTimelineEvent]] ={
    import DeploymentGraphService._
    implicit val hc: HeaderCarrier = HeaderCarrier()

    for {
      data                 <- releasesConnector.deploymentHistoryGraphData(service, start.minus(margin), end.plus(margin)).recover { case Upstream4xxResponse(_) => Map.empty }
      dataWithPlaceholders  = data.map {
        case (env, Nil)  => env -> noEventsPlaceholder(env, start,end)
        case (env, data) => env -> data
      }
      clippedData  = dataWithPlaceholders.mapValues(d => clipTimeline(d, start, end)).values.flatten.toSeq.sortBy(_.env)
    } yield clippedData
  }
}

object DeploymentGraphService {

  val notDeployedMessage = "Not Deployed"

  // generates a placeholder timeline event so that the timeline still shows the environment but with nothing deployed in it
  def noEventsPlaceholder(env: String, start: Instant, end: Instant): Seq[DeploymentTimelineEvent] =
    Environment.parse(env).fold(Seq.empty[DeploymentTimelineEvent])(e => Seq(DeploymentTimelineEvent(e, notDeployedMessage, "", start.plusSeconds(1), end.minusSeconds(1))))

  // Filters a list of timeline events so they fit into a google chart timeline
  // Events outside the date range are dropped except for one event either side which are clipped to the start/end match
  // the date range
  def clipTimeline(data: Seq[DeploymentTimelineEvent], start: Instant, end: Instant): Seq[DeploymentTimelineEvent] = {
    val body  = data.filter(d => d.start.isAfter(start) && d.end.isBefore(end))
    val first = data.filter(_.start.isBefore(start)).lastOption
    val last  = data.find(_.end.isAfter(end))
    (first.toSeq ++ body ++ last.toSeq) match {
      case Nil           => Nil
      case single :: Nil => {
        single match {
          case s if s.start.isAfter(end) && s.end.isAfter(s.start)   => Seq() // not deployed in this range
          case s if s.start.isBefore(start) && s.end.isBefore(start) => Seq() // is this right?
          case s if s.start.isBefore(start) && s.end.isBefore(end)   => Seq(s.copy(start=start, end=end))
          case s if s.start.isBefore(start) && s.end.isAfter(end)    => Seq(s.copy(start=start, end=end))
          case s if s.start.isAfter(start) && s.end.isBefore(end)    => Seq(s)  // single deployment inside range? maybe should extend end?
          case s if s.start.isAfter(start) && s.end.isAfter(end)     => Seq(s.copy(end=end))  // clip end
          case s                                                     => Seq(s.copy(start=max(start, s.start), end=min(end, s.end)))
        }
        // e before start ->
        // s before start e before end-> s=start, e=?
        // s before start e after end-> s=start, e=end
        // s after start e after end -> s=s, e=end
        // s after start e before end -> s=s, e=e
        // s after end -> no events
        // e before start -> no events

  /*      if(first.isDefined) {
          // clip start, stretch end to
          Seq(single.copy(start = start, end = max(end, min(start, single.end))))
        } else if(last.isDefined) {
          Seq(single.copy(start = max(start, single.start), end = end))
        } else {
          Seq(single.copy(start = max(start, single.start), end = min(max(start, single.end), end)))
        }
*/
      }
      case all =>
        all.updated(0,            all.head.copy(start = start, displayStart = Some(min(all.head.start, start))))
           .updated(all.length-1, all.last.copy(end   = end, displayEnd     = Some(max(all.last.end, end))))
    }
  }

  private def max(a: Instant, b: Instant) : Instant = if(a.isAfter(b)) a else b

  private def min(a: Instant, b: Instant) : Instant = if(a.isBefore(b)) a else b

}