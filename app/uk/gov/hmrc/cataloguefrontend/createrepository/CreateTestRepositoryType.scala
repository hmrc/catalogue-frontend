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

package uk.gov.hmrc.cataloguefrontend.createrepository

sealed trait CreateTestRepositoryType { def asString: String}

object CreateTestRepositoryType {
  case object UITestWithScalaTest  extends CreateTestRepositoryType { override def asString: String = "UI Journey Test - with ScalaTest"}
  case object UITestWithCucumber   extends CreateTestRepositoryType { override def asString: String = "UI Journey Test - with Cucumber" }
  case object APITestWithScalaTest extends CreateTestRepositoryType { override def asString: String = "API Test - with ScalaTest"       }
  case object PerformanceTest      extends CreateTestRepositoryType { override def asString: String = "Performance Test"                }

  val values = List(
    UITestWithScalaTest,
    UITestWithCucumber,
    APITestWithScalaTest,
    PerformanceTest
  )

  val parsingError: String =
    s"Not a valid CreateTestRepositoryType. Should be one of ${values.mkString(", ")}"

  def parse(str: String): Option[CreateTestRepositoryType] =
    values.find(_.asString == str)
}
