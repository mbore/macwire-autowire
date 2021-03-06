package com.softwaremill.macwire.autowire

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits._
import com.softwaremill.macwire.autocats.autowire
import com.softwaremill.macwire.autowire.config.{ConfigLoader, CrawlersConfig, DatabaseConfig}
import com.softwaremill.macwire.autowire.crawler.{CrawlerEC, CrawlingService, Worker}
import com.softwaremill.macwire.autowire.database.{DbReaderA, DbReaderB, DbWriterA, DbWriterB}
import com.softwaremill.macwire.autowire.http.HttpApi
import com.softwaremill.macwire.autowire.service.{ServiceA, ServiceB}
import com.softwaremill.tagging._
import doobie.Transactor
import doobie.hikari.HikariTransactor
import sttp.client3.http4s.Http4sBackend

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

  def buildCrawlerEC(cfg: CrawlersConfig): Resource[IO, CrawlerEC] =
    doobie.util.ExecutionContexts.fixedThreadPool[IO](cfg.services.size).map(new CrawlerEC(_))

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
          new DbWriterA(_: Transactor[IO] @@ DbA),
          new DbReaderA(_: Transactor[IO] @@ DbA),
          new DbWriterB(_: Transactor[IO] @@ DbB),
          new DbReaderB(_: Transactor[IO] @@ DbB),
          Http4sBackend.usingDefaultBlazeClientBuilder[IO](),
          buildCrawlers _,
          buildCrawlerEC _
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
