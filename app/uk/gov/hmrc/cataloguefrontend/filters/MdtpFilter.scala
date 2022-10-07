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

package uk.gov.hmrc.cataloguefrontend.filters

import akka.stream.Materializer
import akka.util.ByteString
import play.api.libs.streams.Accumulator
import play.api.mvc._
import scala.concurrent.Promise
import scala.concurrent.Future
import _root_.uk.gov.hmrc.play.http.logging.Mdc

trait MdtpFilter extends EssentialFilter {
  self =>

  implicit def mat: Materializer

  /**
   * Apply the filter, given the request header and a function to call the next
   * operation.
   *
   * @param f A function to call the next operation. Call this to continue
   * normally with the current request. You do not need to call this function
   * if you want to generate a result in a different way.
   * @param rh The RequestHeader.
   */
  def apply(f: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result]

  def apply(next: EssentialAction): EssentialAction = {
    implicit val ec = mat.executionContext
    new EssentialAction {
      def apply(rh: RequestHeader): Accumulator[ByteString, Result] = {
        // Promised result returned to this filter when it invokes the delegate function (the next filter in the chain)
        val promisedResult = Promise[Result]()
        // Promised accumulator returned to the framework
        val bodyAccumulator = Promise[Accumulator[ByteString, Result]]()

        // Invoke the filter
        if (uk.gov.hmrc.play.http.logging.Mdc.mdcData.isEmpty)
          play.api.Logger(getClass).info(s"MdtpFilter - calling apply: MDC: ${uk.gov.hmrc.play.http.logging.Mdc.mdcData}: ${rh.method} ${rh.uri}")
        val result = self.apply({ (rh: RequestHeader) =>
          if (uk.gov.hmrc.play.http.logging.Mdc.mdcData.isEmpty)
            play.api.Logger(getClass).info(s"MdtpFilter - in apply: MDC: ${uk.gov.hmrc.play.http.logging.Mdc.mdcData}: ${rh.method} ${rh.uri}")
          // Invoke the delegate
          bodyAccumulator.success(next(rh))
          val thread = Thread.currentThread.getName
          promisedResult.future.map { res =>
            if (Thread.currentThread.getName != thread)
              play.api.Logger(getClass).warn(s"MdtpFilter - Thread changed from $thread to ${Thread.currentThread.getName}: MDC2: ${uk.gov.hmrc.play.http.logging.Mdc.mdcData}: ${rh.method} ${rh.uri}")
            res
          }(ExecCtxUtils.prepare(ec))
        })(rh)

        result.onComplete({ resultTry =>
          if (uk.gov.hmrc.play.http.logging.Mdc.mdcData.isEmpty)
            play.api.Logger(getClass).info(s"MdtpFilter - calling result.tryComplete: MDC: ${uk.gov.hmrc.play.http.logging.Mdc.mdcData}: ${rh.method} ${rh.uri}")
          // It is possible that the delegate function (the next filter in the chain) was never invoked by this Filter.
          // Therefore, as a fallback, we try to redeem the bodyAccumulator Promise here with an iteratee that consumes
          // the request body.
          bodyAccumulator.tryComplete(resultTry.map(simpleResult => Accumulator.done(simpleResult)))
        })

        val thread = Thread.currentThread.getName
        Accumulator.flatten(bodyAccumulator.future.map { it =>
            if (Thread.currentThread.getName != thread)
              play.api.Logger(getClass).warn(s"MdtpFilter - Thread changed from $thread to ${Thread.currentThread.getName}: MDC2: ${uk.gov.hmrc.play.http.logging.Mdc.mdcData}: ${rh.method} ${rh.uri}")
          if (uk.gov.hmrc.play.http.logging.Mdc.mdcData.isEmpty)
            play.api.Logger(getClass).info(s"MdtpFilter - calling bodyAccumulator.map: MDC: ${uk.gov.hmrc.play.http.logging.Mdc.mdcData}: ${rh.method} ${rh.uri}")
          it.mapFuture { simpleResult =>
              // When the iteratee is done, we can redeem the promised result that was returned to the filter
              promisedResult.success(simpleResult)
              result
            }
            .recoverWith {
              case t: Throwable =>
                // If the iteratee finishes with an error, fail the promised result that was returned to the
                // filter with the same error. Note, we MUST use tryFailure here as it's possible that a)
                // promisedResult was already completed successfully in the mapM method above but b) calculating
                // the result in that method caused an error, so we ended up in this recover block anyway.
                promisedResult.tryFailure(t)
                result
            }
        }(ExecCtxUtils.prepare(ec)))
      }
    }
  }
}
