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

package uk.gov.hmrc.cataloguefrontend.createarepository

sealed trait CreateRepositoryType { def asString: String}

object CreateRepositoryType {
  case object Empty                            extends CreateRepositoryType { override def asString: String = "Empty"}
  case object FrontendMicroservice             extends CreateRepositoryType { override def asString: String = "Frontend microservice"}
  case object FrontendMicroserviceWithScaffold extends CreateRepositoryType { override def asString: String = "Frontend microservice - with scaffold" }
  case object FrontendMicroserviceWithMongodb  extends CreateRepositoryType { override def asString: String = "Frontend microservice - with mongodb" }
  case object BackendMicroservice              extends CreateRepositoryType { override def asString: String = "Backend microservice"}
  case object BackendMicroserviceWithMongodb   extends CreateRepositoryType { override def asString: String = "Backend microservice - with mongodb"}
  case object ApiMicroservice                  extends CreateRepositoryType { override def asString: String = "API microservice"}
  case object ApiMicroserviceWithMongodb       extends CreateRepositoryType { override def asString: String = "API microservice - with mongodb"}

  val values = List(
    Empty,
    FrontendMicroservice,
    FrontendMicroserviceWithScaffold,
    FrontendMicroserviceWithMongodb,
    BackendMicroservice,
    BackendMicroserviceWithMongodb,
    ApiMicroservice,
    ApiMicroserviceWithMongodb
  )

  val parsingError = s"Not a valid CreateRepositoryType. Should be one of ${values.mkString(", ")}"

  def parse(str: String): Option[CreateRepositoryType] = values.find(_.asString == str)

//  val format: Format[CreateRepositoryType] = new Format[CreateRepositoryType] {
//    override def reads(json: JsValue): JsResult[CreateRepositoryType] = json.validate[String]
//      .flatMap(s => parse(s)
//        .map(crt => JsSuccess(crt))
//        .getOrElse(JsError(parsingError))
//      )
//
//    override def writes(o: CreateRepositoryType): JsValue = JsString(o.asString)
//  }

}
