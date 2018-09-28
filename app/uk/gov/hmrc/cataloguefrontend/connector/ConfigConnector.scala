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

package uk.gov.hmrc.cataloguefrontend.connector

import javax.inject.{Inject, Singleton}
import play.Logger
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.githubclient.GitApiConfig


@Singleton
class ConfigConnector @Inject()(
  http: HttpClient,
  servicesConfig: ServicesConfig
) {

  private val teamsAndServicesBaseUrl: String = servicesConfig.baseUrl("teams-and-repositories")

  private implicit val linkFormats         = Json.format[Link]
  private implicit val environmentsFormats = Json.format[TargetEnvironment]
  private implicit val serviceFormats      = Json.format[RepositoryDetails]

  private implicit val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
  }

  // TODO - where does this belong?
  val gitConf = GitApiConfig.fromFile(s"${System.getProperty("user.home")}/.github/.credentials")

  def serviceConfigYaml(env: String, service: String)(implicit hc: HeaderCarrier): Future[String] = {
    val newHc = hc.withExtraHeaders(("Authorization", s"token ${gitConf.key}"))
    val requestUrl = s"https://raw.githubusercontent.com/hmrc/app-config-$env/master/$service.yaml"
    doCall(requestUrl, newHc)
  }

  def serviceConfigConf(env: String, service: String)(implicit hc: HeaderCarrier): Future[String] = {
    val newHc = hc.withExtraHeaders(("Authorization", s"token ${gitConf.key}"))
    val requestUrl = s"https://raw.githubusercontent.com/hmrc/app-config-$env/master/$service.conf"
    doCall(requestUrl, newHc)
  }

  private def doCall(url: String, newHc: HeaderCarrier) = {
    implicit val hc: HeaderCarrier = newHc
    http.GET(url).map {
      case response: HttpResponse if response.status != 200 => {
        Logger.warn(s"Failed to download config file from $url" )
        ""
      }
      case response: HttpResponse => {
        response.body
      }
    }
  }



}


