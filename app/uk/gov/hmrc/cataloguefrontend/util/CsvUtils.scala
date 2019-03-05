/*
 * Copyright 2019 HM Revenue & Customs
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
  /** convert case classes to csv rows */
  def toCsv(ccs: Seq[AnyRef], ignoreFields: Seq[String] = Seq()): String = {
    val asMap = ccs.map { cc =>
      cc.getClass.getDeclaredFields.foldLeft(Map.empty[String, Any]) { (acc, field) =>
        if (ignoreFields.contains(field.getName)) {
          acc
        } else {
          field.setAccessible(true)
          acc + (field.getName -> field.get(cc))
        }
      }
    }
    val headers = asMap.headOption.map(_.keys.mkString(","))
    val dataRows = asMap.map(_.values.mkString(","))

    headers.map(x => (x +: dataRows).mkString("\n")).getOrElse("No data")
  }
}

object CsvUtils extends CsvUtils