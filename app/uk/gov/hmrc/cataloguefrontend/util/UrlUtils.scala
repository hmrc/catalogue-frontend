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

package uk.gov.hmrc.cataloguefrontend.util

import java.net.URLEncoder

import play.utils.UriEncoding
import play.api.routing.sird.QueryStringParameterExtractor
import play.api.routing.sird.QueryString
import java.net.URI
import java.net.URLDecoder

/** This should be only used for building relative URLs.
  * When building absolute URLs (e.g. for http-verbs), use uk.gov.hmrc.http.StringContextOps
  */
object UrlUtils {
  def encodeQueryParam(param: String): String =
    URLEncoder.encode(param, "UTF-8")

  def encodePathParam(param: String): String =
    UriEncoding.encodePathSegment(param, "UTF-8")

  def buildQueryParams(queryParams: (String, Option[String])*): Seq[(String, String)] =
    queryParams.collect { case (k, Some(v)) => (k, v) }

  private val parameterExtractor = new QueryStringParameterExtractor[Map[String, Seq[String]]] {
    override def unapply(qs: QueryString): Option[Map[String,Seq[String]]] =
      Some(qs)
  }

  def parseUrlEncodedParams(uri: String): Map[String, Seq[String]] = 
    parameterExtractor.unapply(
      URI.create(URLDecoder.decode(uri, "UTF-8"))
    ).getOrElse(Map.empty)
}
