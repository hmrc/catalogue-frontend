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

import akka.dispatch.{DispatcherPrerequisites, ExecutorServiceConfigurator, ExecutorServiceDelegate, ExecutorServiceFactory, ForkJoinExecutorConfigurator}
import com.typesafe.config.Config
import org.slf4j.MDC

import java.util.concurrent.{ExecutorService, ThreadFactory}
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.ExecutionContext
import akka.dispatch.Dispatcher
import akka.dispatch.MessageDispatcher
import akka.dispatch.MessageDispatcherConfigurator
import scala.concurrent.duration._

// based on http://yanns.github.io/blog/2014/05/04/slf4j-mapped-diagnostic-context-mdc-with-play-framework/



/*
class MDCPropagatingExecutorServiceConfigurator(
  config       : Config,
  prerequisites: DispatcherPrerequisites
) extends ExecutorServiceConfigurator(config, prerequisites) {

  class MDCPropagatingExecutorServiceFactory(delegate: ExecutorServiceFactory) extends ExecutorServiceFactory {
    override def createExecutorService: ExecutorService = /*{
      //Executors.newWorkStealingPool(4)
      //Executors.newScheduledThreadPool(10)
      //new CustomThreadPool(2)
      /*val nThreads = 10
      return new ThreadPoolExecutor(nThreads, nThreads,
                                      1L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue[Runnable]())*/
    }*/
      new MDCPropagatingExecutorService(delegate.createExecutorService)
  }

  override def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory =
    new MDCPropagatingExecutorServiceFactory(
      new ForkJoinExecutorConfigurator(config.getConfig("fork-join-executor"), prerequisites)
        .createExecutorServiceFactory(id, threadFactory)
    )
}
*/
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
    val mdcContext = MDC.getCopyOfContextMap

    def execute(r: Runnable) = self.execute { () =>
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

    def reportFailure(t: Throwable) =
      self.reportFailure(t)
  }

  private[this] def setContextMap(context: java.util.Map[String, String]) =
    if (context == null)
      MDC.clear()
    else
      MDC.setContextMap(context)
}

object MDCPropagatorExecutionContext {
  def apply(delegate: ExecutionContext): MDCPropagatorExecutionContext =
    new ExecutionContext with MDCPropagatorExecutionContext {
      override def reportFailure(cause: Throwable): Unit = delegate.reportFailure(cause)
      override def execute(runnable: Runnable): Unit = delegate.execute(runnable)
    }
}

/*
class MDCPropagatingExecutorService(val executor: ExecutorService) extends ExecutorServiceDelegate {

  override def execute(command: Runnable): Unit = {
    val mdcData = MDC.getCopyOfContextMap
    val thread = Thread.currentThread.getName

    executor.execute { () =>
      //println(s">>> Copying MDC ${Option(mdcData).map(_.get("X-Request-ID"))} from $thread to ${Thread.currentThread.getName}")
      val oldMdcData = MDC.getCopyOfContextMap
      setMDC(mdcData)
      try {
        command.run()
      } finally {
        setMDC(oldMdcData)
      }
    }
  }

  private def setMDC(context: java.util.Map[String, String]): Unit =
    if (context == null)
      MDC.clear()
    else
      MDC.setContextMap(context)
}


class MdcExecutionContext(delegate: ExecutionContext, mdcContext: java.util.Map[String, String]) extends ExecutionContextExecutor {
  override def execute(runnable: Runnable) = {
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

  override def reportFailure(t: Throwable) =
    delegate.reportFailure(t)

  override def prepare(): ExecutionContext = {
    val mdcContext = MDC.getCopyOfContextMap // why would have data here, but not as param?
    new MdcExecutionContext(this, mdcContext)
  }
}
*/

/*
class CustomThreadPool(poolSize: Int) extends ExecutorService {

  // FIFO ordering
  private val queue = new LinkedBlockingQueue[Runnable]()
  val workers = new Array[WorkerThread](poolSize)
  for (i <- 0 to poolSize) {
    workers(i) = new WorkerThread()
    workers(i).start()
  }

  def execute(task: Runnable): Unit = {
    //synchronized (queue) {
      queue.add(task)
      queue.notify()
    //}
  }

  class WorkerThread extends Thread {
    override def run(): Unit = {
      var task: Runnable = null

      while (true) {
        //synchronized (queue) {
          while (queue.isEmpty()) {
            try {
              queue.wait()
            } catch {
              case e: InterruptedException =>
                System.out.println("An error occurred while queue is waiting: " + e.getMessage())
            }
          }
          task = queue.poll().asInstanceOf[Runnable]
        //}

        try {
          task.run()
        } catch {
          case e: RuntimeException =>
            System.out.println("Thread pool is interrupted due to an issue: " + e.getMessage());
        }
      }
    }
  }

  override def shutdown(): Unit = {
    println("Shutting down thread pool")
    for (i <- 0 to poolSize) {
      workers(i) = null
    }
  }
}
*/
