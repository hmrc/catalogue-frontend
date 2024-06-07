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

package uk.gov.hmrc.cataloguefrontend.service

import javax.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import uk.gov.hmrc.play.bootstrap.frontend.http.FrontendErrorHandler
import views.html._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CatalogueErrorHandler @Inject()(
  override val messagesApi: MessagesApi
)(implicit
  override val ec: ExecutionContext
) extends FrontendErrorHandler {

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit request: RequestHeader): Future[Html] =
    Future.successful(error_template(pageTitle, heading, message))

  override def notFoundTemplate(implicit request: RequestHeader): Future[Html] =
    Future.successful(error_404_template())

  def forbiddenTemplate(implicit request: RequestHeader): Html =
    error_403_template()

}
