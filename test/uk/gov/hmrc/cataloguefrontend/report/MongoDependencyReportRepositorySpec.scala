/*
 * Copyright 2017 HM Revenue & Customs
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

//package uk.gov.hmrc.cataloguefrontend.report
//
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatest.{BeforeAndAfterEach, FunSuite, LoneElement, OptionValues}
//import org.scalatestplus.play.OneAppPerTest
//import play.api.libs.json.{JsObject, Json}
//
//import uk.gov.hmrc.mongo.MongoSpecSupport
//import uk.gov.hmrc.play.test.UnitSpec
//
//class MongoDependencyReportRepositorySpec extends UnitSpec with LoneElement with MongoSpecSupport with ScalaFutures with OptionValues with BeforeAndAfterEach with OneAppPerTest {
//
//
//  val mongoDependencyReportRepository = new MongoDependencyReportRepository(mongo)
//
//  override def beforeEach() {
//    await(mongoDependencyReportRepository.drop)
//  }
//
//
//  private val timestamp = 1494625868
//
//  "getAllDependencyReports" should {
//    "return all the dependencyReports" in {
//
//      insertDependencyReport(timestamp)
//      insertDependencyReport(timestamp + 1)
//
//      val dependencyReports: Seq[DependencyReport] = await(mongoDependencyReportRepository.getAllDependencyReports)
//
//      dependencyReports.size shouldBe 2
//      dependencyReports should contain theSameElementsAs
//        Seq(
//          DependencyReport(DependencyReportType.ServiceOwnerUpdated, timestamp = timestamp, Json.toJson(ServiceOwnerUpdatedDependencyReportData("Catalogue", "joe.black")).as[JsObject]),
//          DependencyReport(DependencyReportType.ServiceOwnerUpdated, timestamp = timestamp + 1, Json.toJson(ServiceOwnerUpdatedDependencyReportData("Catalogue", "joe.black")).as[JsObject])
//        )
//    }
//  }
//
//  private def insertDependencyReport(theTimestamp: Int) = {
//    await(mongoDependencyReportRepository.collection.insert(Json.obj(
//      "dependencyReportType" -> "ServiceOwnerUpdated",
//      "data" -> Json.obj(
//        "service" -> "Catalogue",
//        "username" -> "joe.black"
//      ),
//      "timestamp" -> theTimestamp,
//      "metadata" -> Json.obj()
//    )))
//  }
//
//  "getDependencyReportsByType" should {
//    "return all the right dependencyReports" in {
//      val serviceOwnerUpdateDependencyReport = DependencyReport(DependencyReportType.ServiceOwnerUpdated, timestamp = timestamp, Json.toJson(ServiceOwnerUpdatedDependencyReportData("Catalogue", "Joe Black")).as[JsObject])
//      val otherDependencyReport = DependencyReport(DependencyReportType.Other, timestamp = timestamp, Json.toJson(ServiceOwnerUpdatedDependencyReportData("Catalogue", "Joe Black")).as[JsObject])
//
//      await(mongoDependencyReportRepository.add(serviceOwnerUpdateDependencyReport))
//
//      val dependencyReports: Seq[DependencyReport] = await(mongoDependencyReportRepository.getDependencyReportsByType(DependencyReportType.ServiceOwnerUpdated))
//
//      dependencyReports.size shouldBe 1
//      dependencyReports.head shouldBe DependencyReport(DependencyReportType.ServiceOwnerUpdated, timestamp = timestamp, Json.toJson(ServiceOwnerUpdatedDependencyReportData("Catalogue", "Joe Black")).as[JsObject])
//    }
//  }
//
//  "add" should {
//    "be able to insert a new record and update it as well" in {
//      val dependencyReport = DependencyReport(DependencyReportType.ServiceOwnerUpdated, timestamp = timestamp, Json.toJson(ServiceOwnerUpdatedDependencyReportData("Catalogue", "Joe Black")).as[JsObject])
//      await(mongoDependencyReportRepository.add(dependencyReport))
//      val all = await(mongoDependencyReportRepository.getAllDependencyReports)
//
//      all.size shouldBe 1
//      val savedDependencyReport: DependencyReport = all.loneElement
//
//      savedDependencyReport shouldBe dependencyReport
//    }
//  }
//
//}
