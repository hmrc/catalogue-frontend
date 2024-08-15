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

  val teams =
    s"""
      [
        {
          "name": "teamA",
          "lastActiveDate": "$lastActiveAt",
          "repos": ["repo-one", "repo-two", "repo-three", "repo-four", "repo-five", "repo-six", "repo-seven"]
        },
        {
          "name": "teamB",
          "lastActiveDate": "$lastActiveAt",
          "repos": ["repo-one", "repo-two", "repo-three", "repo-four", "repo-five", "repo-six", "repo-seven", "repo-eight", "repo-nine"]
        },
        {
          "name": "teamC",
          "lastActiveDate": "$lastActiveAt",
          "repos": ["repo-one", "repo-two", "repo-three", "repo-four", "repo-five"]
        },
        {
          "name": "teamD",
          "lastActiveDate": "$lastActiveAt",
          "repos": ["repo-one", "repo-two", "repo-three", "repo-four", "repo-five", "repo-six"]
        },
        {
          "name": "teamE",
          "lastActiveDate": "$lastActiveAt",
          "repos": ["repo-one", "repo-two", "repo-three", "repo-four", "repo-five", "repo-six", "repo-seven"]
        },
        {
          "name": "teamF",
          "lastActiveDate": "$lastActiveAt",
          "repos": ["repo-one", "repo-two", "repo-three", "repo-four", "repo-five", "repo-six", "repo-seven", "repo-eight"]
        },
        {
          "name": "teamG",
          "lastActiveDate": "$lastActiveAt",
          "repos": ["repo-one", "repo-two", "repo-three", "repo-four"]
        },
        {
          "name": "teamH",
          "lastActiveDate": "$lastActiveAt",
          "repos": ["repo-one", "repo-two", "repo-three", "repo-four"]
        },
        {
          "name": "teamI",
          "lastActiveDate": "$lastActiveAt",
          "repos": ["repo-one", "repo-two", "repo-three", "repo-four"]
        },
        {
          "name": "teamJ",
          "lastActiveDate": "$lastActiveAt",
          "repos": ["repo-one", "repo-two", "repo-three", "repo-four"]
        }
      ]
    """

  val repositoriesDataSharedRepo =
    s"""[
     {"name":"01-one-team-repo"   ,"description": "", "teamNames":["teamB"], "owningTeams": ["teamB"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Service","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoa"},
     {"name":"05-five-teams-repo","description": "", "teamNames":["teamB", "teamC", "teamD", "teamE", "teamF"], "owningTeams":["teamB", "teamC", "teamD", "teamE", "teamF"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Library","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repob"},
     {"name":"11-seven-teams-repo"  ,"description": "", "teamNames":["teamA", "teamB", "teamC", "teamD", "teamE", "teamF", "teamG", "teamH", "teamI"], "owningTeams":["teamA", "teamB", "teamC", "teamD", "teamE", "teamF", "teamG", "teamH", "teamI"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Service",  "language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoc"}
     ]
   """"

  val repositoriesSharedRepoSearchResult =
    s"""[
     {"name":"11-seven-teams-repo"  ,"description": "", "teamNames":["teamA", "teamB", "teamC", "teamD", "teamE", "teamF", "teamG", "teamH", "teamI"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Other",  "language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoc"}
     ]
    """"

  val repositoriesData =
    s"""[
       {"name":"teamA-serv"   ,"description": "", "teamNames":["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Service","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoa"},
       {"name":"teamB-library","description": "", "teamNames":["teamB"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Library","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repob"},
       {"name":"teamB-other"  ,"description": "", "teamNames":["teamB"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Other",  "language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoc"}
       ]
     """"

  val repositoriesTeamAData =
    s"""[
       {"name":"teamA-serv"   ,"description": "", "teamNames":["teamA"], "owningTeams": ["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Service","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoa"},
       {"name":"teamA-library","description": "", "teamNames":["teamA"], "owningTeams": ["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Library","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repob"},
       {"name":"teamA-other"  ,"description": "", "teamNames":["teamA"], "owningTeams": ["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Other",  "language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoc"},
       {"name":"teamA-proto"  ,"description": "", "teamNames":["teamA"], "owningTeams": ["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Prototype", "language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoc"},
       {"name":"teamA-proto2"  ,"description": "", "teamNames":["teamA"], "owningTeams": ["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Prototype", "language":"Scala", "isArchived":true, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repoc"}
       ]
     """"

  val digitalServicesData =
    s"""[
          "Digital Service 1",
          "Digital Service 2",
          "Digital Service 3",
          "Digital Service 4",
          "Digital Service 5"
        ]
      """"

  val repositoriesTeamADataLibrary =
    s"""[
       {"name":"teamA-library","description": "", "teamNames":["teamA"], "owningTeams": ["teamA"], "createdDate":"$createdAt", "lastActiveDate":"$lastActiveAt", "repoType":"Library","language":"Scala", "isArchived":false, "defaultBranch":"main", "isDeprecated":false, "url": "http://git/repob"}
       ]
     """"

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
