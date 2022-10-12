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

import akka.dispatch.DispatcherPrerequisites
import com.typesafe.config.Config
import org.slf4j.MDC

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import akka.dispatch.Dispatcher
import akka.dispatch.MessageDispatcher
import akka.dispatch.MessageDispatcherConfigurator
import scala.concurrent.duration._
import akka.dispatch.ExecutorServiceConfigurator
import java.util.concurrent.ThreadFactory
import akka.dispatch.ExecutorServiceFactory
import java.util.concurrent.ExecutorService
import akka.dispatch.ThreadPoolExecutorConfigurator
import java.util.concurrent.Callable
import java.util.Collection

// based on http://yanns.github.io/blog/2014/05/04/slf4j-mapped-diagnostic-context-mdc-with-play-framework/

class MDCPropagatingDispatcherConfigurator(
  config       : Config,
  prerequisites: DispatcherPrerequisites
) extends MessageDispatcherConfigurator(config, prerequisites) {

  override val dispatcher: MessageDispatcher =
    new Dispatcher(
      this,
      config.getString("id"),
      config.getInt("throughput"),
      config.getDuration("throughput-deadline-time", TimeUnit.NANOSECONDS).nanoseconds,
      configureExecutor(),
      config.getDuration("shutdown-timeout", TimeUnit.MILLISECONDS).millis
    ) with MDCPropagatorExecutionContext

}

/**
 * propagates the logback MDC in future callbacks
 */
trait MDCPropagatorExecutionContext extends ExecutionContext {
  self =>

  override def prepare(): ExecutionContext = new ExecutionContext {
    // capture the MDC
    private val mdcContext = MDC.getCopyOfContextMap

    val thread = Thread.currentThread.getName
    //println(s">>>> prepare $thread: $mdcContext, ${Thread.currentThread.getStackTrace.mkString("\n  ")}")

    override def execute(r: Runnable) =
      self.execute { () =>
        //println(s">>>> Copying $mdcContext from $thread into ${Thread.currentThread.getName}")
        // backup the callee MDC context
        val oldMDCContext = MDC.getCopyOfContextMap

        // Run the runnable with the captured context
        setContextMap(mdcContext)
        try {
          r.run()
        } finally {
          // restore the callee MDC context
          setContextMap(oldMDCContext)
        }
      }

    override def reportFailure(t: Throwable) =
      self.reportFailure(t)
  }

  private[this] def setContextMap(context: java.util.Map[String, String]) =
    if (context == null)
      MDC.clear()
    else
      MDC.setContextMap(context)
}
