/*
 * Copyright 2018 HM Revenue & Customs
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
  val digitalServiceNamesData =
    """
      |[
      |   "digital-service-1",
      |   "digital-service-2",
      |   "digital-service-3"
      |]
    """.stripMargin

  val teamsWithRepos =
    """
      |[
      |  {
      |    "name": "team1",
      |    "repos": {
      |      "Service": [
      |        "service1",
      |        "service2"
      |      ],
      |      "Library": ["lib1", "lib2"],
      |      "Prototype": [],
      |      "Other": [
      |        "other1",
      |        "other2"
      |      ]
      |    }
      |  },
      |  {
      |    "name": "team2",
      |    "repos": {
      |      "Service": [
      |        "service3",
      |        "service4"
      |      ],
      |      "Library": ["lib3", "lib4"],
      |      "Prototype": ["prototype1"],
      |      "Other": [
      |        "other3",
      |        "other4"
      |      ]
      |    }
      |  }
      |]
    """.stripMargin

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
       |      "environments" : [
       |      {
       |        "name" : "Dev",
       |        "services" : [{
       |          "name": "jenkins",
       |		       "displayName": "Jenkins",
       |          "url": "https://deploy-dev.co.uk/job/deploy-microservice"
       |        }, {
       |          "name": "grafana",
       |		       "displayName": "Grafana",
       |          "url": "https://grafana-dev.co.uk/#/dashboard"
       |        }]
       |       }, {
       |        "name" : "QA",
       |        "services" : [{
       |          "name": "jenkins",
       |		       "displayName": "Jenkins",
       |          "url": "https://deploy-qa.co.uk/job/deploy-microservice"
       |        }, {
       |          "name": "grafana",
       |		       "displayName": "Grafana",
       |          "url": "https://grafana-datacentred-sal01-qa.co.uk/#/dashboard"
       |        }]
       |      },{
       |        "name" : "Production",
       |        "services" : [{
       |          "name": "jenkins",
       |		       "displayName": "Jenkins",
       |          "url": "https://deploy-prod.co.uk/job/deploy-microservice"
       |        }, {
       |          "name": "grafana",
       |		       "displayName": "Grafana",
       |          "url": "https://grafana-prod.co.uk/#/dashboard"
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

  val prototypeDetailsData =
    s"""
       |{
       |  "name": "2fa-prototype",
       |  "description": "some description",
       |  "createdAt": ${createdAt.epochMillis},
       |  "lastActive": ${lastActiveAt.epochMillis},
       |  "repoType": "Prototype",
       |  "teamNames": [
       |  "CATO",
       |  "Designers"
       |  ],
       |  "githubUrls": [
       |  {
       |    "name": "github-enterprise",
       |    "displayName": "Github Enterprise",
       |    "url": "https://github.gov.uk/HMRC/2fa-prototype"
       |  }
       |  ],
       |  "ci": [ ],
       |  "environments": [ ]
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

  val jobExecutionTimeData =
    """
      |[
      |    {
      |        "period":"2016-05",
      |        "from":"2016-03-01",
      |        "to":"2016-05-31"
      |    },
      |    {
      |        "period":"2016-06",
      |        "from":"2016-04-01",
      |        "to":"2016-06-30",
      |        "duration":{
      |            "median":623142
      |        }
      |    },
      |    {
      |        "period":"2016-07",
      |        "from":"2016-05-01",
      |        "to":"2016-07-31",
      |        "duration":{
      |            "median":632529
      |        }
      |    },
      |    {
      |        "period":"2016-08",
      |        "from":"2016-06-01",
      |        "to":"2016-08-31",
      |        "duration":{
      |            "median":632529
      |        }
      |    },
      |    {
      |        "period":"2016-09",
      |        "from":"2016-07-01",
      |        "to":"2016-09-30",
      |        "duration":{
      |            "median":637234
      |        }
      |    },
      |    {
      |        "period":"2016-10",
      |        "from":"2016-08-01",
      |        "to":"2016-10-31"
      |    },
      |    {
      |        "period":"2016-11",
      |        "from":"2016-09-01",
      |        "to":"2016-11-30"
      |    },
      |    {
      |        "period":"2016-12",
      |        "from":"2016-10-01",
      |        "to":"2016-12-31"
      |    },
      |    {
      |        "period":"2017-01",
      |        "from":"2016-11-01",
      |        "to":"2017-01-31"
      |    },
      |    {
      |        "period":"2017-02",
      |        "from":"2016-12-01",
      |        "to":"2017-02-28"
      |    },
      |    {
      |        "period":"2017-03",
      |        "from":"2017-01-01",
      |        "to":"2017-03-31"
      |    },
      |    {
      |        "period":"2017-04",
      |        "from":"2017-02-01",
      |        "to":"2017-04-25",
      |        "duration":{
      |            "median":645861
      |        }
      |    }
      |]""".stripMargin


  val digitalServiceData =
    """
      |{
      |  "name": "service-1",
      |  "lastUpdatedAt": 1494240869000,
      |  "repositories": [
      |    {
      |      "name": "catalogue-frontend",
      |      "createdAt": "2016-02-24T15:08:50",
      |      "lastUpdatedAt": "2017-05-08T10:53:29",
      |      "repoType": "Service",
      |      "teamNames": ["Team1", "Team2"]
      |    },
      |    {
      |      "name": "repository-jobs",
      |      "createdAt": "2017-04-11T13:14:29",
      |      "lastUpdatedAt": "2017-05-08T10:54:29",
      |      "repoType": "Service",
      |      "teamNames": ["Team1"]
      |    },
      |    {
      |      "name": "teams-and-repositories",
      |      "createdAt": "2016-02-05T10:55:16",
      |      "lastUpdatedAt": "2017-05-08T10:53:58",
      |      "repoType": "Service",
      |      "teamNames": ["Team2"]
      |    }
      |  ]
      |}
    """.stripMargin

}
