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

package uk.gov.hmrc.cataloguefrontend.util

trait CsvUtils {

  /** generate csv */
  def toCsv(rows: Seq[Map[String, String]]): String =
    rows.headOption
      .map { first =>
        val keys                       = first.keys.toList
        val dataRows: Seq[Seq[String]] = rows.map(row => keys.map(key => row.getOrElse(key, "")))
        (keys +: dataRows).map(_.mkString(",")).mkString("\n")
      }
      .getOrElse("No data")

  /** Convert case classes to map seq with reflection.
    * Can then be converted to Csv.
    */
  def toRows(ccs: Seq[AnyRef], ignoreFields: Seq[String] = Seq()): Seq[Map[String, String]] =
    ccs.map { cc =>
      cc.getClass.getDeclaredFields.foldLeft(Map.empty[String, String]) { (acc, field) =>
        if (ignoreFields.contains(field.getName))
          acc
        else {
          field.setAccessible(true)
          acc + (field.getName -> field.get(cc).toString)
        }
      }
    }
}

object CsvUtils extends CsvUtils
