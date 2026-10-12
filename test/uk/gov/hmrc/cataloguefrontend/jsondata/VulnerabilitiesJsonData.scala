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

package uk.gov.hmrc.cataloguefrontend.jsondata

object VulnerabilitiesJsonData {
    val data = 
      s"""
        {
          "shortestRemainingSlaDays": 5,
          "hasBreachedSla": true,
          "byPriority": {
            "immediate": 2,
            "expedite": 3,
            "schedule": 0,
            "monitor": 5
          },
          "byState": {
            "actionable": 2,
            "notApplicable": 1,
            "noFixAvailable": 3,
            "riskAccepted": 1,
            "resolved": 4
          },
          "affectedServices": 2
        }
      """
}