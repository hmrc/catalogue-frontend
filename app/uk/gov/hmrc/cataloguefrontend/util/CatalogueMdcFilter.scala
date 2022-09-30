/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.util

import akka.stream.Materializer
import org.slf4j.MDC
import play.api.{Configuration, Logger}
import play.api.mvc.{Filter, RequestHeader, Result}
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendHeaderCarrierProvider
import uk.gov.hmrc.play.http.logging.Mdc

import scala.concurrent.{ExecutionContext, Future}
import javax.inject.{Inject, Singleton}

@Singleton
class CatalogueMdcFilter @Inject()(
  val mat: Materializer,
  val configuration: Configuration,
  val ec: ExecutionContext
) extends Filter with FrontendHeaderCarrierProvider {

  private val logger = Logger(getClass)

  implicit val ec2: ExecutionContext = ec

  private val warnMdcDataLoss = configuration.getOptional[Boolean]("bootstrap.mdcdataloss.warn").getOrElse(false)

  override def apply(f: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = {
    val headerCarrier = hc(rh)

    val data = Set(
      headerCarrier.requestId.map(HeaderNames.xRequestId    -> _.value),
      headerCarrier.sessionId.map(HeaderNames.xSessionId    -> _.value),
      headerCarrier.forwarded.map(HeaderNames.xForwardedFor -> _.value)
    ).flatten

    data.foreach {
      case (k, v) =>
        MDC.put(k, v)
    }

    f(rh).map { res =>
      val mdcData = Mdc.mdcData.toSet
      import scala.collection.JavaConverters._
      if (warnMdcDataLoss && !data.forall(mdcData.contains)) {
        logger.warn(s"MDC Data has been dropped. endpoint: ${rh.method} ${rh.uri}")
        logger.info(s"expected: $data, found: $mdcData lost=${!data.forall(mdcData.contains)}: ${rh.uri}")
      }
      res
    }
  }
}
