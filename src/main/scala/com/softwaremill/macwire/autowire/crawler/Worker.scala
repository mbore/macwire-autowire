package com.softwaremill.macwire.autowire.crawler

import cats.effect.{IO, Resource}
import cats.implicits._
import com.softwaremill.macwire.autowire.config.CrawlerConfig
import com.softwaremill.macwire.autowire.service.{ServiceA, ServiceB}

class Worker(crawlingService: CrawlingService, serviceA: ServiceA, serviceB: ServiceB, cfg: CrawlerConfig) {
  def work(): IO[Unit] = for {
    result <- crawlingService.crawl(cfg.address)
    _ <- serviceA.write(result)
    _ <- serviceB.write(result)
    _ <- IO.sleep(cfg.sleep)
    _ <- work()
  } yield ()
}

object Worker {
  def apply(crawlingService: CrawlingService, serviceA: ServiceA, serviceB: ServiceB, cfg: CrawlerConfig): Resource[IO, Worker] = {
    val worker = new Worker(crawlingService, serviceA, serviceB, cfg)
    Resource.make(IO.println(s"Start worker with cfg [$cfg]").as(worker))(_ => IO.println(s"Clean up worker [$cfg]"))
  }
}
