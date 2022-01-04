package com.softwaremill.macwire.autowire

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.softwaremill.macwire.autowire.config.{ConfigLoader, CrawlersConfig, DatabaseConfig}
import com.softwaremill.macwire.autowire.crawler.{CrawlingService, Worker}
import com.softwaremill.macwire.autowire.database.{DbReaderA, DbReaderB, DbWriterA, DbWriterB}
import com.softwaremill.macwire.autowire.http.HttpApi
import doobie.hikari.HikariTransactor
import sttp.client3.http4s.Http4sBackend
import cats.implicits._
import com.softwaremill.macwire.autowire.service.{ServiceA, ServiceB}
import io.prometheus.client.CollectorRegistry
import com.softwaremill.macwire.autocats.autowire
import com.softwaremill.tagging._
import doobie.Transactor

case class Crawlers(value: Vector[Resource[IO, Worker]])
case class Dependencies(crawlers: Crawlers, api: HttpApi)

object Main extends IOApp {
  def buildCrawlers(
      cfg: CrawlersConfig,
      crawlingService: CrawlingService,
      serviceA: ServiceA,
      serviceB: ServiceB
  ): Crawlers = Crawlers(cfg.services.map(Worker(crawlingService, serviceA, serviceB, _)))

  def buildTansactor(cfg: DatabaseConfig): Resource[IO, HikariTransactor[IO]] = {
    for {
      connectEC <- doobie.util.ExecutionContexts.fixedThreadPool[IO](cfg.connectThreadPoolSize)
      xa <- HikariTransactor.newHikariTransactor[IO](
        cfg.driver,
        cfg.url,
        cfg.username,
        cfg.password,
        connectEC
      )

    } yield xa
  }

  override def run(args: List[String]): IO[ExitCode] =
    ConfigLoader
      .loadConfig()
      .to[Resource[IO, *]]
      .flatMap { cfg =>
        trait DbA
        trait DbB

        autowire[Dependencies](
          cfg.httpServer,
          cfg.crawlers,
          buildTansactor(cfg.dbA).taggedWithF[DbA],
          buildTansactor(cfg.dbB).taggedWithF[DbB],
          (xa: Transactor[IO] @@ DbA) => new DbWriterA(xa),
          (xa: Transactor[IO] @@ DbA) => new DbReaderA(xa),
          (xa: Transactor[IO] @@ DbB) => new DbWriterB(xa),
          (xa: Transactor[IO] @@ DbB) => new DbReaderB(xa),
          Http4sBackend.usingDefaultBlazeClientBuilder[IO](),
          CollectorRegistry.defaultRegistry,
          buildCrawlers _
        )
      }
      //TODO start whole set of crawlers
      .use(deps => deps.crawlers.value.traverse(_.use(_.work()).start) >> deps.api.resource.use(_ => IO.never))
      .attempt
      .map {
        case Left(_)  => ExitCode.Error
        case Right(_) => ExitCode.Success
      }
}
