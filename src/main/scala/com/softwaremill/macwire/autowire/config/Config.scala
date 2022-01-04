package com.softwaremill.macwire.autowire.config

import scala.concurrent.duration.FiniteDuration

case class Config(dbA: DatabaseConfig, dbB: DatabaseConfig, httpServer: HttpServerConfig, crawlers: CrawlersConfig)

case class DatabaseConfig(driver: String, url: String, username: String, password: String, connectThreadPoolSize: Int)

case class HttpServerConfig(host: String, port: Int)

case class CrawlersConfig(services: Vector[CrawlerConfig])

case class CrawlerConfig(address: String, sleep: FiniteDuration)
