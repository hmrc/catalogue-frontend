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

package uk.gov.hmrc.cataloguefrontend.search


import play.twirl.api.Html
import play.twirl.api.HtmlFormat.fill
import play.twirl.api.HtmlFormat.escape

import java.util.regex.Pattern

trait Highlighter {
  def apply(text: String): Html
}

object NoHighlighter extends Highlighter {
  override def apply(text: String): Html = escape(text)
}

class BoldHighlighter(terms: Seq[String]) extends Highlighter {
  private val rx = Pattern.compile(terms.map(t => s"(${Pattern.quote(t)})").mkString("|") , Pattern.CASE_INSENSITIVE)

  def apply(text: String):Html = {
    val matcher = rx.matcher(text)

    val res = Iterator.continually(matcher).takeWhile(_.find()).foldLeft( (scala.collection.immutable.Seq.empty[Html], 0)) {
      case ( (l, last)  , cur) => (
        l ++ Seq(
          escape(text.substring(last, cur.start())),
          Html("<b>"),
          escape(text.substring(cur.start(), cur.end())),
          Html("</b>"),
      ), cur.end())
    }
    fill(res._1 :+ escape(text.substring(res._2, text.length)))
  }
}
