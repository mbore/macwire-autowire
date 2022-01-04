package com.softwaremill.macwire.autowire.http

import cats.data.{Kleisli, OptionT}
import cats.effect.{IO, Resource}
import com.softwaremill.macwire.autowire.config.HttpServerConfig
import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.CollectorRegistry
import org.http4s.{HttpRoutes, Request}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.metrics.prometheus.Prometheus
import org.http4s.server.Router
import org.http4s.server.middleware.Metrics
import sttp.tapir.server.http4s.Http4sServerInterpreter

class HttpApi(
    endpoints: Endpoints,
    collectorRegistry: CollectorRegistry,
    config: HttpServerConfig
) extends StrictLogging {
  private val routes = Http4sServerInterpreter[IO]().toRoutes(endpoints.all)

  lazy val mainRoutes: HttpRoutes[IO] = loggingMiddleware(routes)

  lazy val resource: Resource[IO, org.http4s.server.Server] =
    Prometheus
      .metricsOps[IO](collectorRegistry)
      .map(m => Metrics[IO](m)(mainRoutes))
      .map { monitoredRoutes =>
        Router(
          s"/" -> monitoredRoutes
        ).orNotFound
      }
      .flatMap { app =>
        BlazeServerBuilder[IO]
          .bindHttp(config.port, config.host)
          .withHttpApp(app)
          .resource
      }

  private def loggingMiddleware(service: HttpRoutes[IO]): HttpRoutes[IO] = Kleisli { req: Request[IO] =>
    OptionT(for {
      _ <- IO(logger.debug(s"Starting request to: ${req.uri.path}"))
      r <- service(req).value
    } yield r)
  }

}