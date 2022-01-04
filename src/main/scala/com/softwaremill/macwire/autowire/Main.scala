package com.softwaremill.macwire.autowire

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.softwaremill.macwire.autowire.config.{ConfigLoader, CrawlersConfig, DatabaseConfig}
import com.softwaremill.macwire.autowire.crawler.{CrawlingService, Worker}
import com.softwaremill.macwire.autowire.database.{DbReaderA, DbReaderB, DbWriterA, DbWriterB}
import com.softwaremill.macwire.autowire.http.{Endpoints, HttpApi}
import doobie.hikari.HikariTransactor
import sttp.client3.http4s.Http4sBackend
import cats.implicits._
import com.softwaremill.macwire.autowire.service.{ServiceA, ServiceB}
import io.prometheus.client.CollectorRegistry

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
        buildTansactor(cfg.dbA).flatMap { xaA =>
          val dbReaderA = new DbReaderA(xaA)
          val dbWriterA = new DbWriterA(xaA)

          val serviceA = new ServiceA(dbReaderA, dbWriterA)
          buildTansactor(cfg.dbB).flatMap { xaB =>
            val dbReaderB = new DbReaderB(xaB)
            val dbWriterB = new DbWriterB(xaB)

            val serviceB = new ServiceB(dbReaderB, dbWriterB)
            Http4sBackend.usingDefaultBlazeClientBuilder[IO]().map { client =>
              val collectorRegistry = CollectorRegistry.defaultRegistry
              val crawlingService = new CrawlingService(client)

              val crawlers = buildCrawlers(cfg.crawlers, crawlingService, serviceA, serviceB)

              val endpoint = new Endpoints(serviceA, serviceB)
              val api = new HttpApi(endpoint, collectorRegistry, cfg.httpServer)

              Dependencies(crawlers, api)
            }
          }
        }
      }
      .use(deps => deps.crawlers.value.traverse(_.use(_.work().start)) >> deps.api.resource.use(_ => IO.never))
      .map(_ => ExitCode.Success)

}
