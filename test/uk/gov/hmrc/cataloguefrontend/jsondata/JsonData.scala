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

import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterStatusValue, ShutterType}

import java.time.Instant

object JsonData {

  val emptyList = "[]"

  val createdAt    = Instant.parse("2016-04-23T16:45:30.00Z")
  val lastActiveAt = Instant.parse("2016-10-12T10:30:12.00Z")

  val serviceJenkinsBuildData: String =
    """{
      "jobs": [
        {
           "jobName": "service-1-pipeline",
           "jobType": "pipeline",
           "jenkinsURL": "http://jenkins/service-1/"
        },
        {
           "jobName": "service-1-performance",
           "jobType": "performance",
           "jenkinsURL": "http://jenkins/service-1/"
        },
        {
           "jobName": "service-1",
           "jobType": "job",
           "jenkinsURL": "http://jenkins/service-1/"
        }
      ]
    }"""

  val teamDetailsData =
    """
      {
        "name" : "teamA",
        "repos": {
          "Service": [
            "service1",
            "service2"
          ],
          "Library": [
            "library1",
            "library2"
          ],
          "Other": [
            "other1",
            "other2"
          ]
        },
        "ownedRepos": []
      }
    """

  val teamsAndRepositories =
    s"""[$teamDetailsData]"""

  val prototypeDetailsData =
    s"""{
      "name": "2fa-prototype",
      "description": "some description",
      "url": "https://github.com/HMRC/2fa-prototype",
      "createdDate": "$createdAt",
      "lastActiveDate": "$lastActiveAt",
      "isPrivate": true,
      "repoType": "Prototype",
      "owningTeams": [],
      "language": "HTML",
      "isArchived": false,
      "defaultBranch": "main",
      "isDeprecated": false,
      "teamNames": [
        "CATO",
        "Designers"
      ]
    }"""

  val libraryDetailsData =
    """{
        "name": "serv",
        "description": "",
        "isPrivate": false,
        "isArchived": false,
        "description": "some description",
        "createdDate": "2016-02-24T15:08:50Z",
        "lastActiveDate": "2016-11-08T10:55:55Z",
        "repoType": "Library",
        "defaultBranch": "main",
        "owningTeams": [
          "The True Owners"
        ],
        "teamNames": ["teamA", "teamB"],
        "url": "https://github.com/hmrc/serv"
    }"""

  val deploymentThroughputData =
    """[
        {
          "period": "2015-11",
          "from": "2015-12-01",
          "to": "2016-02-29",
          "throughput": {
            "leadTime": {
              "median": 6
            },
            "interval": {
              "median": 1
            }
          },
          "stability": {
            "hotfixRate": 23,
            "hotfixLeadTime": {
              "median": 6
            }
          }
        },
        {
          "period": "2015-12",
          "from": "2015-12-01",
          "to": "2016-02-29",
          "throughput": {
            "leadTime": {
              "median": 6
            },
            "interval": {
              "median": 5
            }
          },
          "stability": {
            "hotfixRate": 23,
            "hotfixLeadTime": {
              "median": 6
            }
          }
        },
        {
          "period": "2016-01",
          "from": "2015-12-01",
          "to": "2016-02-29",
          "throughput": {
            "leadTime": {
              "median": 6
            },
            "interval": {
              "median": 6
            }
          }
        }
      ]
      """

  val jobExecutionTimeData =
    """[
        {
          "period":"2016-05",
          "from":"2016-03-01",
          "to":"2016-05-31"
        },
        {
          "period":"2016-06",
          "from":"2016-04-01",
          "to":"2016-06-30",
          "duration":{
            "median":623142
          }
        },
        {
          "period":"2016-07",
          "from":"2016-05-01",
          "to":"2016-07-31",
          "duration":{
            "median":632529
          }
        },
        {
          "period":"2016-08",
          "from":"2016-06-01",
          "to":"2016-08-31",
          "duration":{
            "median":632529
          }
        },
        {
          "period":"2016-09",
          "from":"2016-07-01",
          "to":"2016-09-30",
          "duration":{
            "median":637234
          }
        },
        {
          "period":"2016-10",
          "from":"2016-08-01",
          "to":"2016-10-31"
        },
        {
          "period":"2016-11",
          "from":"2016-09-01",
          "to":"2016-11-30"
        },
        {
          "period":"2016-12",
          "from":"2016-10-01",
          "to":"2016-12-31"
        },
        {
          "period":"2017-01",
          "from":"2016-11-01",
          "to":"2017-01-31"
        },
        {
          "period":"2017-02",
          "from":"2016-12-01",
          "to":"2017-02-28"
        },
        {
          "period":"2017-03",
          "from":"2017-01-01",
          "to":"2017-03-31"
        },
        {
          "period":"2017-04",
          "from":"2017-02-01",
          "to":"2017-04-25",
          "duration":{
            "median":645861
          }
        }
      ]"""


  val serviceConfigsServiceEmpty =
    "[]"

  val serviceConfigsServiceService1 =
    """[
        {
          "environment": "qa",
          "routes": [
            {
              "frontendPath": "/test/qa/ccc",
              "ruleConfigurationUrl": "https://github.com/hmrc/mdtp-frontend-routes/blob/main/production/frontend-proxy-application-rules.conf#L29",
              "isRegex": false
            }
          ]
        },
        {
          "environment": "production",
          "routes": [
            {
              "frontendPath": "/test/prod/ccc",
              "ruleConfigurationUrl": "https://github.com/hmrc/mdtp-frontend-routes/blob/main/production/frontend-proxy-application-rules.conf#L29",
              "isRegex": false
            }
          ]
        },
        {
          "environment": "development",
          "routes": [
            {
              "frontendPath": "/test/dev/ccc",
              "ruleConfigurationUrl": "https://github.com/hmrc/mdtp-frontend-routes/blob/main/production/frontend-proxy-application-rules.conf#L29",
              "isRegex": false
            }
          ]
        }
      ]
    """

  val deploymentConfigsService1 = """[{"instances": 1, "slots": 1, "environment": "production", "zone": "protected"}]"""

  val dependenciesWithRuleViolation =
  """{
   "repositoryName" : "catalogue-frontend",
   "lastUpdated" : "2018-12-14T15:45:07.335Z",
   "sbtPluginsDependencies" : [
      {
         "currentVersion" : {
            "minor" : 1,
            "original" : "1.1.0",
            "major" : 1,
            "patch" : 0
         },
         "isExternal" : false,
         "latestVersion" : {
            "minor" : 1,
            "original" : "1.1.0",
            "patch" : 0,
            "major" : 1
         },
         "name" : "sbt-distributables",
         "bobbyRuleViolations" : []
      },
      {
         "isExternal" : false,
         "latestVersion" : {
            "patch" : 0,
            "major" : 1,
            "minor" : 13,
            "original" : "1.13.0"
         },
         "name" : "sbt-auto-build",
         "currentVersion" : {
            "original" : "1.13.0",
            "minor" : 13,
            "major" : 1,
            "patch" : 0
         },
         "bobbyRuleViolations" : [
            {
               "range" : {
                  "range" : "(,1.4.0)",
                  "upperBound" : {
                     "version" : {
                        "original" : "1.4.0",
                        "minor" : 4,
                        "patch" : 0,
                        "major" : 1
                     },
                     "inclusive" : false
                  }
               },
               "reason" : "Play 2.5 upgrade",
               "from" : "2017-05-01"
            }
         ]
      }
   ],
   "otherDependencies" : [
      {
         "isExternal" : false,
         "latestVersion" : {
            "minor" : 13,
            "original" : "0.13.17",
            "major" : 0,
            "patch" : 17
         },
         "currentVersion" : {
            "patch" : 17,
            "major" : 0,
            "minor" : 13,
            "original" : "0.13.17"
         },
         "name" : "sbt",
         "bobbyRuleViolations" : []
      }
   ],
   "libraryDependencies" : [
      {
         "latestVersion" : {
            "minor" : 2,
            "original" : "3.2.0",
            "major" : 3,
            "patch" : 0
         },
         "isExternal" : false,
         "currentVersion" : {
            "major" : 3,
            "patch" : 0,
            "original" : "3.0.0",
            "minor" : 0
         },
         "name" : "hmrctest",
         "bobbyRuleViolations" : []
      },
      {
         "name" : "simple-reactivemongo",
         "isExternal" : false,
         "latestVersion" : {
            "patch" : 0,
            "major" : 7,
            "minor" : 3,
            "original" : "7.3.0-play-26"
         },
         "currentVersion" : {
            "major" : 7,
            "patch" : 0,
            "original" : "7.0.0-play-26",
            "minor" : 0
         },
         "bobbyRuleViolations" : [
            {
               "range" : {
                  "lowerBound" : {
                     "inclusive" : true,
                     "version" : {
                        "original" : "7.0.0",
                        "minor" : 0,
                        "major" : 7,
                        "patch" : 0
                     }
                  },
                  "upperBound" : {
                     "inclusive" : true,
                     "version" : {
                        "minor" : 7,
                        "original" : "7.7.0",
                        "major" : 7,
                        "patch" : 0
                     }
                  },
                  "range" : "[7.0.0,7.7.0]"
               },
               "reason" : "Uses ReactiveMongo [0.15.0, 0.16.0] which has problems with reconnecting",
               "from" : "2019-02-06"
            }
         ]
      }
   ]
}
"""

  val serviceData: String =
    """{
        "name": "serv",
        "isPrivate": false,
        "isArchived": false,
        "repoType": "Service",
        "owningTeams": [ "The True Owners" ],
        "teamNames": ["teamA", "teamB"],
        "description": "some description",
        "createdAt": "2016-02-24T15:08:50Z",
        "lastActive": "2016-11-08T10:55:55Z",
        "defaultBranch": "main",
        "githubUrl": {
          "name": "github",
          "displayName": "github.com",
          "url": "https://github.com/hmrc/serv"
        },
        "environments" : [
          {
            "name" : "Production",
            "services" : [
              {
                "name": "ser1",
                "displayName": "service1",
                "url": "http://ser1/serv"
              }, {
                "name": "ser2",
                "displayName": "service2",
                "url": "http://ser2/serv"
              }
            ]
          }, {
            "name" : "Staging",
            "services" : [
              {
                "name": "ser1",
                "displayName": "service1",
                "url": "http://ser1/serv"
              }, {
                "name": "ser2",
                "displayName": "service2",
                "url": "http://ser2/serv"
              }
            ]
          }
        ]
      }
    """

  val libraryData: String = {
    """{
        "name"           : "lib",
        "isPrivate"      : false,
        "isArchived"     : false,
        "description"    : "some description",
        "createdDate"    : "2016-02-24T15:08:50Z",
        "lastActiveDate" : "2016-11-08T10:55:55Z",
        "repoType"       : "Library",
        "language"       : "Scala",
        "owningTeams"    : [ "The True Owners" ],
        "teamNames"      : ["teamA", "teamB"],
        "defaultBranch"  : "main",
        "url"            : "https://github.com/hmrc/lib"
      }
    """
  }

  def repositoryModulesAllVersions(name: String, dependenciesCompile: String) =
    s"""[{
    "name"             : "$name",
    "version"          : "v1",
    "dependenciesBuild": [],
    "modules"          : [{
      "name"                : "m1",
      "group"               : "g1",
      "dependenciesCompile" : $dependenciesCompile,
      "dependenciesProvided": [],
      "dependenciesTest"    : [],
      "dependenciesIt"      : [],
      "crossScalaVersions"  : [],
      "activeBobbyRules"    : [],
      "pendingBobbyRules"   : []
    }]
  }]"""

  def repositoryModules(name: String, dependenciesCompile: String) =
    s"""[{
      "name"             : "$name",
      "version"          : "v1",
      "dependenciesBuild": [],
      "modules"          : [{
        "name"                : "m1",
        "group"               : "g1",
        "dependenciesCompile" : $dependenciesCompile,
        "dependenciesProvided": [],
        "dependenciesTest"    : [],
        "dependenciesIt"      : [],
        "crossScalaVersions"  : [],
        "activeBobbyRules"    : [],
        "pendingBobbyRules"   : []
      }]
    }]"""

  def dependencies =
    """[{
      "name"               : "dn",
      "group"              : "dg",
      "currentVersion"     : "dv",
      "bobbyRuleViolations": []
    }]"""

  val indicatorData: String =
    """[
        {
          "period":"2015-11",
          "leadTime":{
            "median":6
          },
          "interval":{
            "median":1
          }
        },
        {
          "period":"2015-12",
          "leadTime":{
            "median":6
          },
          "interval":{
            "median":5
          }
        },
        {
          "period":"2016-01",
          "leadTime":{
            "median":6
          },
          "interval":{
            "median":6
          }
        }
      ]
    """

  def shutterApiData(shutterType: ShutterType, env: Environment, status: ShutterStatusValue) =
    s"""
      {
        "name": "serv",
        "type": "${shutterType.asString}",
        "environment": "${env.asString}",
        "status": {
           "value": "${status.asString}",
           "useDefaultOutagePage": false
        }
      }
    """

  val profiles: String =
    "[]"

  val serviceDependenciesData: String =
    """{
        "uri": "/",
        "name": "service-name",
        "version": "1.0.0",
        "runnerVersion": "1.0.0",
        "java": {
          "version": "1.0.0",
          "vendor": "openjdk",
          "kind": ""
        },
        "classpath": "",
        "dependencies": []
      }"""

  val serviceRelationships: String =
    """{
      |  "inboundServices": ["service-a", "service-b"],
      |  "outboundServices": ["service-c", "service-d"]
      |}
      |""".stripMargin
}
