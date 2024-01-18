package uk.gov.hmrc.cataloguefrontend.connector

import play.api.Logging
import play.api.libs.json.Reads
import uk.gov.hmrc.cataloguefrontend.connector.model.UserLog
import uk.gov.hmrc.cataloguefrontend.users.User
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlatopsAuditingConnector @Inject()(
  httpClientV2  : HttpClientV2
  , servicesConfig: ServicesConfig
)(implicit
  ec: ExecutionContext
) extends Logging {

  import HttpReads.Implicits._

  private val baseUrl = servicesConfig.baseUrl("platops-auditing")

  def userLog(user: User)(implicit hc: HeaderCarrier): Future[Option[UserLog]] = {
    val url: URL = url"$baseUrl/platops-auditing/getUserLog?userName=${user.username}"

    implicit val ltr: Reads[UserLog] = UserLog.userLogFormat

    httpClientV2
      .get(url)
      .execute[Option[UserLog]]
  }
}
