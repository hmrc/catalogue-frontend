package uk.gov.hmrc.cataloguefrontend.config

import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Environment}
import play.api.Mode.Mode
import uk.gov.hmrc.play.config.{ServicesConfig => HmrcServicesConfig}

@Singleton
class ServicesConfig @Inject()(override val runModeConfiguration: Configuration, environment: Environment)
    extends HmrcServicesConfig {

  override protected lazy val mode: Mode = environment.mode
}
