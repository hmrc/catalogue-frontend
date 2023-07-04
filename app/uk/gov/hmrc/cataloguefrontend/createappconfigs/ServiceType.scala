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

///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.cataloguefrontend.createappconfigs
//
//sealed trait ServiceType { def asString: String}
//
//object ServiceType {
//  case object Frontend extends ServiceType { override def asString: String = "Frontend"}
//  case object Backend  extends ServiceType { override def asString: String = "Backend"}
//
//
//  val values = List(
//    Frontend,
//    Backend
//  )
//
//  val parsingError = s"Not a valid Service Type. Should be one of ${values.mkString(", ")}"
//
//  def parse(str: String): Option[ServiceType] = values.find(_.asString == str)
//
//}
//
