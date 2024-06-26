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

import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, __}
import play.api.mvc.{EssentialAction, RequestHeader}
import play.api.routing.Router.Attrs
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.filters.AuditFilter
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendHeaderCarrierProvider

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class CatalogueFrontendAuditFilter @Inject()(
  auditConnector : AuditConnector,
  configuration  : Configuration
)(using
  ec: ExecutionContext
) extends AuditFilter
     with FrontendHeaderCarrierProvider:

  override def apply(nextFilter: EssentialAction): EssentialAction =
    (rh: RequestHeader) =>
      val headerCarrier = hc(rh)
      nextFilter(rh).map: res =>
        if (needsAuditing(rh))
          auditConnector.sendExplicitAudit(
            auditType = "FrontendInteraction",
            detail    = Detail(
                          username        = rh.session.data.getOrElse("username", default = "GuestUser")
                        , uri             = rh.headers.get("Raw-Request-URI").getOrElse("-")
                        , statusCode      = res.header.status
                        , method          = rh.method
                        , userAgentString = rh.headers.get("User-Agent").getOrElse("-")
                        , deviceID        = rh.headers.get("Cookie").getOrElse("-")
                        , referrer        = rh.headers.get("Referer").getOrElse("-")
                        )
          )(headerCarrier, ec, Detail.format)
        res

  private def needsAuditing(rh: RequestHeader): Boolean =
    configuration.get[Boolean]("auditing.enabled")
      && rh.attrs.get(Attrs.HandlerDef).map(_.controller).forall(controllerNeedsAuditing)

  private def controllerNeedsAuditing(controllerName: String): Boolean =
    configuration.getOptional[Boolean](s"controllers.$controllerName.needsAuditing").getOrElse(true)

end CatalogueFrontendAuditFilter

case class Detail(
  username       : String,
  uri            : String,
  statusCode     : Int,
  method         : String,
  userAgentString: String,
  deviceID       : String,
  referrer       : String,
)

object Detail:
  val format: Format[Detail] =
    ( ( __ \ "username"       ).format[String]
    ~ ( __ \ "uri"            ).format[String]
    ~ ( __ \ "statusCode"     ).format[Int]
    ~ ( __ \ "method"         ).format[String]
    ~ ( __ \ "userAgentString").format[String]
    ~ ( __ \ "deviceID"       ).format[String]
    ~ ( __ \ "referrer"       ).format[String]
    )(Detail.apply, d => Tuple.fromProductTyped(d))
