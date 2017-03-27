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

import java.time.{LocalDateTime, ZoneId}

/**
  * Created by armin.
  */
object JsonData {

  import DateHelper._
  val createdAt = LocalDateTime.of(2016, 5, 23, 16, 45, 30)
  val lastActiveAt = LocalDateTime.of(2016, 10, 12, 10, 30, 12)

  val repositoriesData =
    s"""|[
        |{"name":"teamA-serv", "createdAt": ${createdAt.epochMillis}, "lastUpdatedAt": ${lastActiveAt.epochMillis}, "repoType":"Service"},
        |{"name":"teamB-library", "createdAt": ${createdAt.epochMillis}, "lastUpdatedAt": ${lastActiveAt.epochMillis}, "repoType":"Library"},
        |{"name":"teamB-other", "createdAt": ${createdAt.epochMillis}, "lastUpdatedAt": ${lastActiveAt.epochMillis}, "repoType":"Other"}
        |]""".stripMargin

  val serviceDetailsData =
    s"""
       |    {
       |	     "name": "service-1",
       |      "description": "some description",
       |      "createdAt": ${createdAt.epochMillis},
       |      "lastActive": ${lastActiveAt.epochMillis},
       |      "repoType": "Service",
       |      "teamNames": ["teamA", "teamB"],
       |	     "githubUrls": [{
       |		     "name": "github",
       |        "displayName": "github.com",
       |		     "url": "https://github.com/hmrc/service-1"
       |	     }],
       |	     "ci": [
       |		     {
       |		       "name": "open1",
       |		       "displayName": "open 1",
       |		       "url": "http://open1/service-1"
       |		     },
       |		     {
       |		       "name": "open2",
       |		       "displayName": "open 2",
       |		       "url": "http://open2/service-2"
       |		     }
       |	     ],
       |      "environments" : [{
       |        "name" : "env1",
       |        "services" : [{
       |          "name": "ser1",
       |		       "displayName": "service-1",
       |          "url": "http://ser1/service-1"
       |        }, {
       |          "name": "ser2",
       |		       "displayName": "service-2",
       |          "url": "http://ser2/service-2"
       |        }]
       |      },{
       |        "name" : "env2",
       |        "services" : [{
       |          "name": "ser1",
       |		       "displayName": "service-1",
       |          "url": "http://ser1/service-1"
       |        }, {
       |          "name": "ser2",
       |		       "displayName": "service-2",
       |          "url": "http://ser2/service-2"
       |        }]
       |       }]
       |     }
    """.stripMargin

  val teamDetailsData =
    """
      |{
      |    "name" : "teamA",
      |    "repos": {
      |    "Service": [
      |            "service1",
      |            "service2"
      |    ],
      |    "Library": [
      |            "library1",
      |            "library2"
      |    ],
      |    "Other": [
      |            "other1",
      |            "other2"
      |    ]
      |    }
      |}
    """.stripMargin

  val libraryDetailsData =
    """
      |    {
      |	     "name": "serv",
      |      "description": "some description",
      |      "createdAt": 1456326530000,
      |      "lastActive": 1478602555000,
      |      "repoType": "Library",
      |      "teamNames": ["teamA", "teamB"],
      |	     "githubUrls": [{
      |		     "name": "github",
      |        "displayName": "github.com",
      |		     "url": "https://github.com/hmrc/serv"
      |	     }],
      |	     "ci": [
      |		     {
      |		       "name": "open1",
      |		       "displayName": "open 1",
      |		       "url": "http://open1/serv"
      |		     },
      |		     {
      |		       "name": "open2",
      |		       "displayName": "open 2",
      |		       "url": "http://open2/serv"
      |		     }
      |	     ]
      |     }
    """.stripMargin

  val deploymentThroughputData =
    """[
      |    {
      |        "period": "2015-11",
      |        "from": "2015-12-01",
      |        "to": "2016-02-29",
      |        "throughput": {
      |            "leadTime": {
      |                "median": 6
      |            },
      |            "interval": {
      |                "median": 1
      |            }
      |        },
      |        "stability": {
      |            "hotfixRate": 23,
      |            "hotfixLeadTime": {
      |                "median": 6
      |            }
      |        }
      |    },
      |    {
      |        "period": "2015-12",
      |        "from": "2015-12-01",
      |        "to": "2016-02-29",
      |        "throughput": {
      |            "leadTime": {
      |                "median": 6
      |            },
      |            "interval": {
      |                "median": 5
      |            }
      |        },
      |        "stability": {
      |            "hotfixRate": 23,
      |            "hotfixLeadTime": {
      |                "median": 6
      |            }
      |        }
      |    },
      |    {
      |        "period": "2016-01",
      |        "from": "2015-12-01",
      |        "to": "2016-02-29",
      |        "throughput": {
      |            "leadTime": {
      |                "median": 6
      |            },
      |            "interval": {
      |                "median": 6
      |            }
      |        }
      |    }
      |]
      | """.stripMargin

}
