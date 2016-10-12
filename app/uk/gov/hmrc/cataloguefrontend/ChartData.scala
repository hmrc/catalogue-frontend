/*
 * Copyright 2016 HM Revenue & Customs
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

import scala.xml.{Elem, NodeSeq}

case class ChartData(rows: Seq[Html]) {
  def isEmpty = rows.isEmpty
}

object ChartData {

  def deploymentThroughput(serviceName: String, dataPoints: Option[Seq[DeploymentThroughputDataPoint]]): Option[ChartData] = {

    dataPoints.map { points =>
      ChartData(chartRowsThroughput(serviceName, points))
    }
  }

  def deploymentStability(serviceName: String, dataPoints: Option[Seq[DeploymentStabilityDataPoint]]): Option[ChartData] = {

    dataPoints.map { points =>
      ChartData(chartRowsStability(serviceName, points))
    }
  }


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

  def toPercent(r: Double): Int = (r * 100).toInt

  private def getReleaseUrlAnchor(serviceName: String, from: LocalDate, to: LocalDate) = <a href={releasesUrl(serviceName, dateToString(from), dateToString(to))}>View releases</a>

  private def releasesUrl(serviceName: String, from: String, to: String) = s"${uk.gov.hmrc.cataloguefrontend.routes.CatalogueController.releases().url}?serviceName=${serviceName}&from=${from}&to=${to}"

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
