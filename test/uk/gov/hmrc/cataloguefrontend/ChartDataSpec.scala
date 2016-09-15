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

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.OptionValues._
import play.twirl.api.Html


class ChartDataSpec extends WordSpec with Matchers with TypeCheckedTripleEquals {


  def asDocument(html: String): Document = Jsoup.parse(html)

  def getRowColumns(row: Html): Seq[String] = {
    row.toString().replaceAll("\\[", "").split(",").toSeq.map(_.trim)
  }

  "ChartData" should {

    "return correct html rows for given data points" in {
      val endDate: LocalDate = LocalDate.of(2016, 12, 31)
      val threeMonthsBeforeEndDate = endDate.minusMonths(3)
      val sixMonthsBeforeEndDate = endDate.minusMonths(6)
      val nineMonthsBeforeEndDate = endDate.minusMonths(9)

      val points: Seq[FprDataPoint] = Seq(
        FprDataPoint("2016-12", threeMonthsBeforeEndDate, endDate, Some(MedianDataPoint(100)), Some(MedianDataPoint(500))),
        FprDataPoint("2016-09", sixMonthsBeforeEndDate, threeMonthsBeforeEndDate.minusDays(1), Some(MedianDataPoint(1000)), Some(MedianDataPoint(5000))),
        FprDataPoint("2016-06", nineMonthsBeforeEndDate, sixMonthsBeforeEndDate.minusDays(1), Some(MedianDataPoint(10000)), Some(MedianDataPoint(50000)))
      )

      val data: Option[ChartData] = ChartData("my_test_service", Some(points))

      val chartData: ChartData = data.value
      val rows: Seq[Html] = chartData.rows

      rows.size should ===(3)

      rows(0).toString().startsWith("[") shouldBe true
      rows(0).toString().endsWith("]") shouldBe true
      getRowColumns(rows(0))(0) should === (""""2016-12"""")
      getRowColumns(rows(0))(1) should === ("""100""")

      val leadTimeToolTip = asDocument(getRowColumns(rows(0))(2))

      val leadTimeToolTipTableData: Elements = leadTimeToolTip.select("tr td")
      leadTimeToolTipTableData.get(0).text() should include("Period:")
      leadTimeToolTipTableData.get(0).text() should include("2016-12")
      leadTimeToolTipTableData.get(1).text() should include("Lead Time:")
      leadTimeToolTipTableData.get(1).text() should include("100")

      leadTimeToolTipTableData.get(2).select("a").attr("href") shouldBe "/releases?serviceName=my_test_service&from=30-09-2016&to=31-12-2016"
      leadTimeToolTipTableData.get(2).select("a").text() shouldBe "See releases"

      getRowColumns(rows(0))(3) should === ("""500""")

      val intervalToolTip = asDocument(getRowColumns(rows(0))(4))
      val intervalToolTipTableData: Elements = intervalToolTip.select("tr td")
      intervalToolTipTableData.get(0).text() should include("Period:")
      intervalToolTipTableData.get(0).text() should include("2016-12")
      intervalToolTipTableData.get(1).text() should include("Interval:")
      intervalToolTipTableData.get(1).text() should include("500")

      intervalToolTipTableData.get(2).select("a").attr("href") shouldBe "/releases?serviceName=my_test_service&from=30-09-2016&to=31-12-2016"
      intervalToolTipTableData.get(2).select("a").text() shouldBe "See releases"



    }
  }


}
