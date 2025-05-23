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

object CsvUtils:
  /** generate csv */
  def toCsv(rows: Seq[Seq[(String, String)]], includeTitle: Boolean = true): String =
    rows.headOption
      .map: first =>
        val keys                       = first.map(_._1)
        val dataRows: Seq[Seq[String]] = rows.map(row => keys.map(key => row.find(_._1 == key).fold("")(_._2)))
        ((if includeTitle then keys else Nil) +: dataRows).map(_.mkString(",")).mkString("\n")
      .getOrElse("No data")
