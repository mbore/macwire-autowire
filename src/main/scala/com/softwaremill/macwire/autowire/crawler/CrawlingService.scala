package com.softwaremill.macwire.autowire.crawler

import cats.effect.IO
import sttp.client3.SttpBackend

import scala.concurrent.ExecutionContext

class CrawlingService(client: SttpBackend[IO, Any], crawlerEC: CrawlerEC) {
  def crawl(address: String): IO[String] = IO { println(s"crawled [$address]"); s"Result for [$address]" }.evalOn(crawlerEC.underlyingEC)
}

class CrawlerEC(val underlyingEC: ExecutionContext) extends AnyVal
