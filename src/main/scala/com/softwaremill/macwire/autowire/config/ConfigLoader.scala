package com.softwaremill.macwire.autowire.config

import cats.effect.IO
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._

object ConfigLoader {
  def loadConfig(): IO[Config] = ConfigSource.default.loadF[IO, Config]()
}
