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

package uk.gov.hmrc.cataloguefrontend.connector

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.cataloguefrontend.model.TeamName

import java.time.Instant

class TeamsAndRepositoriesConnectorSpec
  extends AnyWordSpec
     with Matchers
     with BeforeAndAfterEach
     with ScalaFutures
     with IntegrationPatience
     with GuiceOneAppPerSuite
     with WireMockSupport
     with TypeCheckedTripleEquals
     with OptionValues
     with EitherValues:

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(Map(
        "microservice.services.teams-and-repositories.host" -> wireMockHost,
        "microservice.services.teams-and-repositories.port" -> wireMockPort,
      ))
      .build()

  private lazy val teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector =
    app.injector.instanceOf[TeamsAndRepositoriesConnector]

  given HeaderCarrier = HeaderCarrier()

  "findTestJobs" should:
    "return test jobs" in:
      stubFor(
        get(urlEqualTo("/api/test-jobs?teamName=team"))
          .willReturn(aResponse().withBody("""
            [{
              "repoName"  : "serviceA",
              "jobName"   : "serviceAJob",
              "jenkinsURL": "http.jenkins/serviceAJob",
              "jobType"   : "test",
              "testType"  : "acceptance"
            }, {
              "repoName"  : "serviceB",
              "jobName"   : "serviceBJob",
              "jenkinsURL": "http.jenkins/serviceBJob",
              "jobType"   : "test",
              "testType"  : "performance"
            }]
          """)
        )
      )

      val response = teamsAndRepositoriesConnector
        .findTestJobs(teamName = Some(TeamName("team")), digitalService = None)
        .futureValue

      response shouldBe Seq(
        JenkinsJob(repoName = "serviceA", jobName = "serviceAJob" , jenkinsURL = "http.jenkins/serviceAJob", jobType = BuildJobType.Test, testType = Some(TestType.Acceptance ), latestBuild = None),
        JenkinsJob(repoName = "serviceB", jobName = "serviceBJob" , jenkinsURL = "http.jenkins/serviceBJob", jobType = BuildJobType.Test, testType = Some(TestType.Performance), latestBuild = None)
      )

  "lookupLatestJenkinsJobs" should:
    "return a Link if exists" in:
      stubFor(
        get(urlEqualTo("/api/v2/repositories/serviceA/jenkins-jobs"))
          .willReturn(aResponse().withBody("""
            {
              "jobs": [{
                "repoName"  : "serviceA",
                "jobName"   : "serviceA",
                "jenkinsURL": "http.jenkins/serviceA",
                "jobType"   : "job"
              }, {
                "repoName"  : "serviceA",
                "jobName"   : "serviceA-pr-builder",
                "jenkinsURL": "http.jenkins/serviceA-pr-builder",
                "jobType"   : "pull-request"
              }, {
                "repoName"  : "serviceA",
                "jobName"   : "serviceA-pipeline",
                "jenkinsURL": "http.jenkins/serviceA-pipeline",
                "jobType"   : "pipeline"
              }]
            }
          """)
        )
      )

      val response = teamsAndRepositoriesConnector
        .lookupLatestJenkinsJobs("serviceA")
        .futureValue

      response shouldBe Seq(
        JenkinsJob(repoName = "serviceA", jobName = "serviceA"           , jenkinsURL = "http.jenkins/serviceA"           , jobType = BuildJobType.Job        , testType = None, latestBuild = None),
        JenkinsJob(repoName = "serviceA", jobName = "serviceA-pr-builder", jenkinsURL = "http.jenkins/serviceA-pr-builder", jobType = BuildJobType.PullRequest, testType = None, latestBuild = None),
        JenkinsJob(repoName = "serviceA", jobName = "serviceA-pipeline"  , jenkinsURL = "http.jenkins/serviceA-pipeline"  , jobType = BuildJobType.Pipeline   , testType = None, latestBuild = None)
      )

  "openPullRequestsForReposOwnedByTeam" should :
    "return all open pull requests for repositories owned by a team" in :
      val now = Instant.now()

      stubFor(
        get(urlEqualTo("/api/open-pull-requests?reposOwnedByTeam=teamA"))
          .willReturn(aResponse().withBody(
            s"""
            [
            {
                "repoName" : "service-a",
                "title" : "pr title 1",
                "url" : "https://github.com/hmrc/service-a/pull/1",
                "author" : "author1",
                "createdAt" : "$now"
            },
            {
                "repoName" : "service-b",
                "title" : "pr title 2",
                "url" : "https://github.com/hmrc/service-b/pull/1",
                "author" : "author2",
                "createdAt" : "$now"
            }
            ]
          """)
          )
      )

      val response = teamsAndRepositoriesConnector
        .openPullRequestsForReposOwnedByTeam(TeamName("teamA"))
        .futureValue

      response shouldBe Seq(
        OpenPullRequest("service-a", "pr title 1", "https://github.com/hmrc/service-a/pull/1", "author1", now),
        OpenPullRequest("service-b", "pr title 2", "https://github.com/hmrc/service-b/pull/1", "author2", now),
      )

  "openPullRequestsRaisedByMembersOfTeam" should :
    "return all open pull requests raised by members of a team" in :
      val now = Instant.now()

      stubFor(
        get(urlEqualTo("/api/open-pull-requests?raisedByMembersOfTeam=teamA"))
          .willReturn(aResponse().withBody(
            s"""
            [
            {
                "repoName" : "service-a",
                "title" : "pr title 1",
                "url" : "https://github.com/hmrc/service-a/pull/1",
                "author" : "author1",
                "createdAt" : "$now"
            },
            {
                "repoName" : "service-b",
                "title" : "pr title 2",
                "url" : "https://github.com/hmrc/service-b/pull/1",
                "author" : "author2",
                "createdAt" : "$now"
            }
            ]
          """)
          )
      )

      val response = teamsAndRepositoriesConnector
        .openPullRequestsRaisedByMembersOfTeam(TeamName("teamA"))
        .futureValue

      response shouldBe Seq(
        OpenPullRequest("service-a", "pr title 1", "https://github.com/hmrc/service-a/pull/1", "author1", now),
        OpenPullRequest("service-b", "pr title 2", "https://github.com/hmrc/service-b/pull/1", "author2", now),
      )
