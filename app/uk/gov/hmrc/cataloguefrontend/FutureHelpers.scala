/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.Inject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class FutureHelpers @Inject()(metrics: Metrics) {

  lazy val defaultRegistry: MetricRegistry = metrics.defaultRegistry

  def withTimerAndCounter[T](
    name: String)(f: Future[T], mayBeAuxFailurePredicate: Option[T => Boolean] = None): Future[T] = {
    val t = defaultRegistry.timer(s"$name.timer").time()

    def logFailure(): Unit = {
      t.stop()
      defaultRegistry.counter(s"$name.failure").inc()
    }

    def logSuccess(): Unit = {
      t.stop()
      defaultRegistry.counter(s"$name.success").inc()
    }

    def doAuxFailurePredicate(s: T): Unit =
      mayBeAuxFailurePredicate.foreach { auxFailurePredicate =>
        if (auxFailurePredicate(s)) {
          logFailure()
        }
      }

    f.andThen {
      case Success(s) =>
        logSuccess()
        doAuxFailurePredicate(s)
      case Failure(_) =>
        logFailure()
    }
  }

  object FutureIterable {
    def apply[A](listFuture: Iterable[Future[A]]): Future[Iterable[A]] = Future.sequence(listFuture)
  }

  def continueOnError[A](f: Future[A]): Future[Try[A]] =
    f.map(Success(_)).recover { case x => Failure(x) }
}
