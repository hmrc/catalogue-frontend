/*
 * Copyright 2016 HM Revenue & Customs
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

import org.joda.time.DateTime
import play.api.libs.json._

class CachedList[T](val data: Seq[T], val time: DateTime) extends Seq[T] {
  override def length: Int = data.length
  override def apply(idx: Int): T = data.apply(idx)
  override def iterator: Iterator[T] = data.iterator
}

object CachedList {
  import play.api.libs.functional.syntax._

  implicit def cachedListFormats[T](implicit fmt: Format[T]): Format[CachedList[T]] = new Format[CachedList[T]] {
    private val writes: Writes[CachedList[T]] = (
      (JsPath \ "data").write[Seq[T]] and
        (JsPath \ "cacheTimestamp").write[DateTime]
      ) (x => (x.data, x.time))

    private val reads: Reads[CachedList[T]] = (
      (JsPath \ "data").read[Seq[T]] and
        (JsPath \ "cacheTimestamp").read[DateTime]
      )(new CachedList(_, _))

    override def writes(o: CachedList[T]): JsValue = writes.writes(o)
    override def reads(json: JsValue): JsResult[CachedList[T]] = reads.reads(json)
  }
}
