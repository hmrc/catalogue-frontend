/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.auditing

import org.apache.pekko.stream.Materializer
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.{OFormat, __}
import play.api.mvc.{Filter, RequestHeader, Result}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendHeaderCarrierProvider
import play.api.libs.functional.syntax._


import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CatalogueFrontendAuditFilter @Inject()(
  override val mat: Materializer,
  auditConnector : AuditConnector,
)(implicit
  ec: ExecutionContext,
)
  extends Filter with FrontendHeaderCarrierProvider {

  override def apply(next: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = {
    val headerCarrier = hc(rh)
    next(rh).map { res =>
      auditConnector.sendExplicitAudit(
      	auditType =  "FrontendInteraction",
      	detail = Detail(
          username        = rh.session.data.get("username")  .getOrElse("GuestUser")
        , uri             = rh.headers.get("Raw-Request-URI").getOrElse("-")
        , statusCode      = res.header.status
        , userAgentString = rh.headers.get("User-Agent")     .getOrElse("-")
        , deviceID        = rh.headers.get("Cookie")         .getOrElse("-")
        , referrer        = rh.headers.get("Referer")        .getOrElse("-")
        )
      )(headerCarrier, ec, Detail.format)
      res
    }
  }
}


case class Detail(
  username: String,
  uri: String,
  statusCode: Int,
  userAgentString: String,
  deviceID: String,
  referrer: String,
)

object Detail {
  val format: OFormat[Detail] = {
    ( ( __ \ "username"   ).format[String]
    ~ ( __ \ "uri"        ).format[String]
    ~ ( __ \ "statusCode" ).format[Int]
    ~ ( __ \ "userAgentString" ).format[String]
    ~ ( __ \ "deviceID" ).format[String]
    ~ ( __ \ "referrer" ).format[String]
    )(Detail.apply, unlift(Detail.unapply))
  }
}
