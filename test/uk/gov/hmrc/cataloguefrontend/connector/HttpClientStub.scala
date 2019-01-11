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

package uk.gov.hmrc.cataloguefrontend.connector

import cats.data.OptionT
import cats.implicits._
import com.typesafe.config.Config
import org.scalatest.Matchers._
import play.api.libs.json.{JsValue, Writes}
import play.api.libs.ws.WSClient
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.ws.WSHttp

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait HttpClientStub {

  val expect = new VerbStubbing

  private sealed trait Verb
  private object Verb {
    type GET = GET.type
    case object GET extends Verb {
      override val toString: String = "GET"
    }
    type POST = POST.type
    case object POST extends Verb {
      override val toString: String = "POST"
    }
  }

  private sealed trait ResponseExpectation {
    val url: String
    val verb: Verb

    protected val responseBuilder: ResponseBuilder

    def httpResponse: Future[HttpResponse] = responseBuilder.httpResponse.getOrElse {
      throw new IllegalStateException(s"No response defined for $verb $url")
    }
  }

  private object ResponseExpectation {

    case class NoPayloadResponseExpectation(
      verb: Verb,
      url: String,
      headerCarrier: HeaderCarrier,
      protected val responseBuilder: ResponseBuilder)
        extends ResponseExpectation

    case class PayloadResponseExpectation(
      verb: Verb,
      url: String,
      headerCarrier: HeaderCarrier,
      payload: JsValue,
      protected val responseBuilder: ResponseBuilder)
        extends ResponseExpectation
  }

  import ResponseExpectation._

  class VerbStubbing {
    self =>

    private var expectationsUnderBuilt: Queue[ResponseExpectation] = Queue.empty

    private[HttpClientStub] def popExpectation[EXPECTATION <: ResponseExpectation](verb: Verb)(
      implicit expectationClassTag: ClassTag[EXPECTATION]): EXPECTATION = {
      val (currentExpectations, leftExpectations) = expectationsUnderBuilt.dequeueOption.getOrElse {
        throw new IllegalStateException(s"No http client responses defined")
      }
      expectationsUnderBuilt = leftExpectations

      if (currentExpectations.verb != verb)
        throw new IllegalStateException(s"Expected $verb but current verb is ${currentExpectations.verb}")

      currentExpectations match {
        case expectation: EXPECTATION => expectation
        case other =>
          throw new IllegalArgumentException(
            s"Expected ${expectationClassTag.getClass.getSimpleName} but current expectation is ${other.getClass}")
      }
    }

    def GET(to: String)(implicit headerCarrier: HeaderCarrier): ResponseBuilder = {
      val responseBuilder = new ResponseBuilder
      self.expectationsUnderBuilt = expectationsUnderBuilt enqueue NoPayloadResponseExpectation(
        Verb.GET,
        to,
        headerCarrier,
        responseBuilder)
      responseBuilder
    }

    def POST(to: String, payload: JsValue)(implicit headerCarrier: HeaderCarrier): ResponseBuilder = {
      val responseBuilder = new ResponseBuilder
      self.expectationsUnderBuilt = expectationsUnderBuilt enqueue PayloadResponseExpectation(
        Verb.POST,
        to,
        headerCarrier,
        payload,
        responseBuilder)
      responseBuilder
    }
  }

  class ResponseBuilder {

    private[HttpClientStub] var httpResponse: OptionT[Future, HttpResponse] = OptionT.none[Future, HttpResponse]

    def returning(status: Int, body: JsValue): Unit =
      returning(HttpResponse(status, responseJson = Some(body)))

    def returning(status: Int): Unit =
      returning(HttpResponse(status, responseJson = None))

    def returning(response: HttpResponse): Unit =
      httpResponse = OptionT.pure[Future](response)

  }

  class ClientStub private[HttpClientStub] (verbStubbing: VerbStubbing) extends HttpClient with WSHttp {

    override val hooks: Seq[HttpHook] = Nil

    override def doGet(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
      val expectation = verbStubbing.popExpectation[NoPayloadResponseExpectation](Verb.GET)

      url shouldBe expectation.url
      hc  shouldBe expectation.headerCarrier

      expectation.httpResponse
    }

    override def doPost[A](url: String, body: A, headers: Seq[(String, String)])(
      implicit wts: Writes[A],
      hc: HeaderCarrier): Future[HttpResponse] = {
      val expectation = verbStubbing.popExpectation[PayloadResponseExpectation](Verb.POST)

      url              shouldBe expectation.url
      hc               shouldBe expectation.headerCarrier
      wts.writes(body) shouldBe expectation.payload

      expectation.httpResponse
    }

    override def POSTString[O](url: String, body: String, headers: Seq[(String, String)])(
      implicit rds: HttpReads[O],
      hc: HeaderCarrier,
      ec: ExecutionContext): Future[O] = ???

    override def POSTForm[O](
      url: String,
      body: Map[String, Seq[String]])(implicit rds: HttpReads[O], hc: HeaderCarrier, ec: ExecutionContext): Future[O] =
      ???

    override def POSTEmpty[O](
      url: String)(implicit rds: HttpReads[O], hc: HeaderCarrier, ec: ExecutionContext): Future[O] =
      ???

    override def doPatch[A](url: String, body: A)(implicit wts: Writes[A], hc: HeaderCarrier): Future[HttpResponse] =
      ???

    override def doPut[A](url: String, body: A)(implicit wts: Writes[A], hc: HeaderCarrier): Future[HttpResponse] =
      ???

    override def doDelete(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
      ???

    override protected def configuration: Option[Config] = ???

    override def wsClient: WSClient = ???
  }

  val httpClient: ClientStub = new ClientStub(expect)
}
