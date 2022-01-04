package com.softwaremill.macwire.autowire.crawler

import cats.effect.IO
import sttp.client3.SttpBackend

class CrawlingService(client: SttpBackend[IO, Any]) {
  def crawl(address: String): IO[String] = IO { println(s"crawled [$address]"); s"Result for [$address]" }
}
