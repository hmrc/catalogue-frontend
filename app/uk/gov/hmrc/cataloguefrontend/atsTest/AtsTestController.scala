/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.atsTest

import play.api.http.HttpEntity
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AtsTestController @Inject()(mcc: MessagesControllerComponents, wsClient: WSClient)(implicit ec: ExecutionContext) extends FrontendController(mcc) {

  def showAtsTestPage: Action[AnyContent] = Action.async {
    wsClient.url("https://s3-ats-migration-test.s3.eu-west-3.amazonaws.com/test.jpg").withMethod("GET").stream().map { response =>
      if (response.status == 200) {
        val contentType = response.headers
          .get("Content-Type")
          .flatMap(_.headOption)
          .getOrElse("application/octet-stream")
        response.headers.get("Content-Length") match {
          case Some(Seq(length)) =>
            Ok.sendEntity(HttpEntity.Streamed(response.bodyAsSource, Some(length.toLong), Some(contentType)))
          case _ =>
            Ok.chunked(response.bodyAsSource).as(contentType)
        }
      } else {
        Ok(s"Test failed. Status=${response.status}, Body=${response.body}")
      }
    }
  }

}
