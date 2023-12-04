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

object TeamsAndRepositoriesJsonData {
  private val createdAt = JsonData.createdAt
  private val lastActiveAt = JsonData.lastActiveAt

  val teams = {
    s"""
      [
        {
          "name": "teamA",
          "createdDate": "$createdAt",
          "lastActiveDate": "$lastActiveAt",
          "repos": 7
        },
        {
          "name": "teamB",
          "createdDate": "$createdAt",
          "lastActiveDate": "$lastActiveAt",
          "repos": 9
        },
        {
          "name": "teamC",
          "createdDate": "$createdAt",
          "lastActiveDate": "$lastActiveAt",
          "repos": 5
        },
        {
          "name": "teamD",
          "createdDate": "$createdAt",
          "lastActiveDate": "$lastActiveAt",
          "repos": 6
        },
        {
          "name": "teamE",
          "createdDate": "$createdAt",
          "lastActiveDate": "$lastActiveAt",
          "repos": 7
        },
        {
          "name": "teamF",
          "createdDate": "$createdAt",
          "lastActiveDate": "$lastActiveAt",
          "repos": 8
        },
        {
          "name": "teamG",
          "createdDate": "$createdAt",
          "lastActiveDate": "$lastActiveAt",
          "repos": 4
        },
        {
          "name": "teamH",
          "createdDate": "$createdAt",
          "lastActiveDate": "$lastActiveAt",
          "repos": 4
        },
        {
          "name": "teamI",
          "createdDate": "$createdAt",
          "lastActiveDate": "$lastActiveAt",
          "repos": 4
        },
        {
          "name": "teamJ",
          "createdDate": "$createdAt",
          "lastActiveDate": "$lastActiveAt",
          "repos": 4
        }
      ]
    """
  }

  val repositoriesDataSharedRepo = {
    s"""[
     {"name":"01-one-team-repo"   ,"description": "", "teamNames":["teamB"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Service","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoa"},
     {"name":"05-five-teams-repo","description": "", "teamNames":["teamB", "teamC", "teamD", "teamE", "teamF"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Library","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repob"},
     {"name":"11-seven-teams-repo"  ,"description": "", "teamNames":["teamA", "teamB", "teamC", "teamD", "teamE", "teamF", "teamG", "teamH", "teamI"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Service",  "language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoc"}
     ]
   """"
  }

  val repositoriesSharedRepoSearchResult = {
    s"""[
     {"name":"11-seven-teams-repo"  ,"description": "", "teamNames":["teamA", "teamB", "teamC", "teamD", "teamE", "teamF", "teamG", "teamH", "teamI"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Other",  "language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoc"}
     ]
    """"
  }

  val repositoriesData = {
    s"""[
       {"name":"teamA-serv"   ,"description": "", "teamNames":["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Service","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoa"},
       {"name":"teamB-library","description": "", "teamNames":["teamB"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Library","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repob"},
       {"name":"teamB-other"  ,"description": "", "teamNames":["teamB"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Other",  "language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoc"}
       ]
     """"
  }

  val repositoriesTeamAData = {
    s"""[
       {"name":"teamA-serv"   ,"description": "", "teamNames":["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Service","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoa"},
       {"name":"teamA-library","description": "", "teamNames":["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Library","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repob"},
       {"name":"teamA-other"  ,"description": "", "teamNames":["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Other",  "language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoc"},
       {"name":"teamA-proto"  ,"description": "", "teamNames":["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Prototype", "language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoc"},
       {"name":"teamA-proto2"  ,"description": "", "teamNames":["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Prototype", "language":"Scala", "isArchived":true, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoc"}
       ]
     """"
  }

  val repositoriesTeamADataLibrary = {
    s"""[
       {"name":"teamA-library","description": "", "teamNames":["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Library","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repob"}
       ]
     """"
  }

  val jenkinsData: String =
    """{
          "jobName": "lib",
          "jobType": "job",
          "jenkinsURL": "http://jenkins/lib/"
        }
        """

  val jenkinsBuildData: String =
    """{ "jobs": [{
            "jobName": "lib",
            "jobType": "job",
            "jenkinsURL": "http://jenkins/lib/"
          }]
        }
        """

  def repositoryData(repoName: String = "service-1") =
    s"""
       {
         "name": "$repoName",
         "isPrivate": false,
         "isArchived": false,
         "isDeprecated": false,
         "description": "some description",
         "createdDate": "$createdAt",
         "lastActiveDate": "$lastActiveAt",
         "repoType": "Service",
         "defaultBranch": "main",
         "teamNames": ["teamA", "teamB"],
         "language":"Scala",
         "url": "https://github.com/hmrc/$repoName"
       }
    """
}
