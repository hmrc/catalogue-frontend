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

package uk.gov.hmrc.cataloguefrontend.shuttering

import cats.implicits._
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class ShutterGroupsConnector @Inject() (
  override val mcc : MessagesControllerComponents,
  httpClientV2     : HttpClientV2,
  servicesConfig   : ServicesConfig,
  override val auth: FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  import HttpReads.Implicits._

  private val gitHubProxyBaseURL: String = servicesConfig.baseUrl("platops-github-proxy")

  val logger = Logger(this.getClass)

  def shutterGroups()(using HeaderCarrier): Future[Seq[ShutterGroup]] = {
    val url = url"$gitHubProxyBaseURL/platops-github-proxy/github-raw/outage-pages/HEAD/conf/shutter-groups.json"
    given Reads[Seq[ShutterGroup]] = ShutterGroup.reads
    httpClientV2
      .get(url)
      .execute[Option[Seq[ShutterGroup]]]
      .map(_.getOrElse {
        logger.info(s"No shutter groups found at $url, defaulting to an empty list")
        Seq.empty[ShutterGroup]
      })
      .recover {
        case NonFatal(ex) =>
          logger.error(s"Problem retrieving shutter groups at $url, defaulting to an empty list: ${ex.getMessage}", ex)
          Seq.empty
      }
  }
}

case class ShutterGroup(
  name    : String,
  services: List[String]
)

object ShutterGroup {

  private given cats.Applicative[JsResult] =
    new cats.Applicative[JsResult] {
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

  val reads = new Reads[Seq[ShutterGroup]] {
    def reads(js: JsValue): JsResult[Seq[ShutterGroup]] =
      js.validate[JsObject]
        .flatMap(
          _.fields.toList.traverse {
            case (name, jsarray) =>
              jsarray.validate[List[String]].map(ShutterGroup(name, _))
          }
        )
  }
}
