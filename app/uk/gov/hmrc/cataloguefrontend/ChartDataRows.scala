/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.twirl.api.Html

import scala.concurrent.duration.Duration
import scala.xml.{Elem, NodeSeq}

case class ChartDataRows(rows: Seq[Html]) {
  def isEmpty = rows.isEmpty
}

trait ChartData {

  def deploymentThroughput(serviceName: String, dataPoints: Option[Seq[DeploymentThroughputDataPoint]]): Option[ChartDataRows] =
    dataPoints.map { points =>
      ChartDataRows(chartRowsThroughput(serviceName, points))
    }

  def deploymentStability(serviceName: String, dataPoints: Option[Seq[DeploymentStabilityDataPoint]]): Option[ChartDataRows] =
    dataPoints.map { points =>
      ChartDataRows(chartRowsStability(serviceName, points))
    }

  def jobExecutionTime(repositoryName: String, dataPoints: Option[Seq[JobExecutionTimeDataPoint]]): Option[ChartDataRows] =
    dataPoints.map { points =>
      ChartDataRows(chartRowsJobExecutionTime(repositoryName, points))
    }

  protected def deploymentsSerachParameters(name: String, from: String, to: String) : Map[String,String]

  private def chartRowsThroughput(serviceName: String, points: Seq[DeploymentThroughputDataPoint]): Seq[Html] = {
    for {
      dp <- points

    } yield {
      val relasePageAnchor: Elem = getReleaseUrlAnchor(serviceName, dp.from, dp.to)
      val tip = toolTip(dp.period, Some(relasePageAnchor)) _
      val leadTimeToolTip = tip("Lead Time", dp.leadTime.map(_.median.toString))
      val intervalToolTip = tip("Interval", dp.interval.map(_.median.toString))

      Html(s"""["${dp.period}", ${unwrapMedian(dp.leadTime)}, "$leadTimeToolTip", ${unwrapMedian(dp.interval)}, "$intervalToolTip"]""")
    }
  }

  private def chartRowsStability(serviceName: String, points: Seq[DeploymentStabilityDataPoint]): Seq[Html] = {
    for {
      dp <- points

    } yield {
      val tip = toolTip(dp.period, Some(getReleaseUrlAnchor(serviceName, dp.from, dp.to))) _

      val hotfixRateToolTip = tip("Hotfix Rate", dp.hotfixRate.map(r => s"${toPercent(r)}%"))
      val hotfixIntervalTip = tip("Hotfix Interval", dp.hotfixInterval.map(_.median.toString))
      Html(s"""["${dp.period}", ${unwrap(dp.hotfixRate)}, "$hotfixRateToolTip", ${unwrapMedian(dp.hotfixInterval)}, "$hotfixIntervalTip"]""")
    }
  }

  private def formatTime(medianDataPoint: MedianDataPoint): String = {
    import scala.concurrent.duration._

    medianDataPoint.median match {
      case median if median < 60000 =>
        s"${(medianDataPoint.median millis).toSeconds}sec"
      case median if median < 3600000 =>
        val minutes = (median millis).toMinutes
        val seconds = (median millis).toSeconds - minutes * 60

        s"${minutes}min ${seconds}sec"
      case median =>
        val hours = (median millis).toHours
        val minutes = (median millis).toMinutes - hours * 60
        val seconds = (median millis).toSeconds - hours * 3600 - minutes * 60

        s"${hours}h ${minutes}min ${seconds}sec"
    }

  }

  private def chartRowsJobExecutionTime(repositoryName: String, points: Seq[JobExecutionTimeDataPoint]): Seq[Html] = {
    import scala.concurrent.duration._

    for {
      dataPoint <- points
    } yield {

      val tip = toolTip(dataPoint.period, None) _
      val jobExecutionTimeTooltip: Html =
        tip("Job Execution Time", dataPoint.duration.map(formatTime))

      val timeData = dataPoint.duration.map(d =>
        s"${(d.median millis).toMinutes}"
      ).getOrElse("null")

      Html(s"""["${dataPoint.period}", $timeData, "$jobExecutionTimeTooltip"]""")
    }
  }

  private def toPercent(r: Double): Int = (r * 100).toInt

  private def getReleaseUrlAnchor(serviceName: String, from: LocalDate, to: LocalDate) = <a href={deploymentsUrl(serviceName, dateToString(from), dateToString(to))}>View deployments</a>

  private def deploymentsUrl(serviceName: String, from: String, to: String) = {
    val paramString: String = deploymentsSerachParameters(serviceName, from, to).toList.map(x => s"${x._1}=${x._2}").mkString("&")
    s"${uk.gov.hmrc.cataloguefrontend.routes.CatalogueController.deploymentsPage().url}?$paramString"
  }

  private def dateToString(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))

  private def unwrapMedian(container: Option[MedianDataPoint]) = container.map(l => s"""${l.median}""").getOrElse("null")

  private def unwrap(container: Option[_]) = container.map(l => s"""$l""").getOrElse("null")

  private def toolTip(period: String, additionalContent: Option[NodeSeq])(dataPointLabel: String, dataPointValue: Option[String]) = {

    val element: Elem =
      <div>
        <table>
          <tr>
            <td nowrap="true">
              <label>Period:</label>{period}
            </td>

          </tr>
          <tr>
            <td nowrap="true">
              <label>
                {dataPointLabel}: </label>{dataPointValue.fold("")(identity)}
            </td>

          </tr>
          <tr>
            <td colspan="2" align="center">
              {additionalContent.getOrElse(NodeSeq.Empty)}
            </td>
          </tr>
        </table>
      </div>

    Html(element.mkString.replaceAll("\"", "'").replaceAll("\n", ""))
  }

}

object ServiceChartData extends ChartData {
  override protected def deploymentsSerachParameters(name: String, from: String, to: String): Map[String, String] = Map(
    "serviceName" -> name,
    "from" -> from,
    "to" -> to
  )
}

object TeamChartData extends ChartData {
  override protected def deploymentsSerachParameters(name: String, from: String, to: String): Map[String, String] = Map(
    "team" -> name,
    "from" -> from,
    "to" -> to
  )
}