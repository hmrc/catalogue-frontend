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

// copied from play.utils.ExecCtxUtils

import org.slf4j.MDC
import java.util.concurrent.{Executor, Executors}
import scala.concurrent.{ExecutionContextExecutor, ExecutionContext}

sealed class ExecCtxUtils {
  final def prepare(ec: ExecutionContext): ExecutionContext = {
    //ec.prepare()
    val mdcContext = MDC.getCopyOfContextMap
    new MdcExecutionContext(ec, mdcContext)
  }
}

object ExecCtxUtils extends ExecCtxUtils


class MdcExecutionContext(delegate: ExecutionContext, mdcContext: java.util.Map[String, String]) extends ExecutionContextExecutor {
  def execute(runnable: Runnable) = {
    delegate.execute { () =>
      val oldMDCContext = MDC.getCopyOfContextMap
      setContextMap(mdcContext)
      try {
        runnable.run()
      } finally {
        setContextMap(oldMDCContext)
      }
    }
  }

  private[this] def setContextMap(context: java.util.Map[String, String]): Unit =
    if (context == null)
      MDC.clear()
    else
      MDC.setContextMap(context)

  def reportFailure(t: Throwable) =
    delegate.reportFailure(t)
}
