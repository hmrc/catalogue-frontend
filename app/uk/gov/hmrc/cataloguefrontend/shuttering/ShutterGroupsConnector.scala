/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.shuttering

import cats.instances.all._
import cats.syntax.all._
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.config.GithubConfig
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, StringContextOps}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class ShutterGroupsConnector @Inject() (
  http      : HttpClient,
  githubConf: GithubConfig
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._

  val logger = Logger(this.getClass)

  def shutterGroups: Future[List[ShutterGroup]] = {
    val url = url"${githubConf.rawUrl}/hmrc/outage-pages/HEAD/conf/shutter-groups.json"
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val gr = ShutterGroup.reads
    http.GET[Option[List[ShutterGroup]]](
      url,
      headers = Seq("Authorization" -> s"token ${githubConf.token}")
    )
      .map(_.getOrElse {
        logger.info(s"No shutter groups found at $url, defaulting to an empty list")
        List.empty[ShutterGroup]
      })
      .recover {
        case NonFatal(ex) =>
          logger.error(s"Problem retrieving shutter groups at $url, defaulting to an empty list: ${ex.getMessage}", ex)
          List.empty
      }
  }
}

case class ShutterGroup(
  name    : String,
  services: List[String]
)

object ShutterGroup {

  private implicit val applicative = new cats.Applicative[JsResult] {
    def pure[A](a: A): JsResult[A] = JsSuccess(a)
    def ap[A, B](ff: JsResult[A => B])(fa: JsResult[A]): JsResult[B] =
      fa match {
        case JsSuccess(a, p1) =>
          ff match {
            case JsSuccess(f, p2) => JsSuccess(f(a), p1)
            case JsError(e1)      => JsError(e1)
          }
        case JsError(e1) => JsError(e1)
      }
  }

  val reads = new Reads[List[ShutterGroup]] {
    def reads(js: JsValue): JsResult[List[ShutterGroup]] =
      js.validate[JsObject]
        .flatMap(
          _.fields.toList.traverse {
            case (name, jsarray) =>
              jsarray.validate[List[String]].map(ShutterGroup(name, _))
          }
        )
  }
}
