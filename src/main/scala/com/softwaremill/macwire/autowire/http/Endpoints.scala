package com.softwaremill.macwire.autowire.http

import sttp.tapir.Tapir
import sttp.tapir.json.circe.TapirJsonCirce
import cats.implicits._
import com.softwaremill.macwire.autowire.service.{ServiceA, ServiceB}

class Endpoints(serviceA: ServiceA, serviceB: ServiceB) extends Tapir with TapirJsonCirce   {
  private val readA = endpoint.get
    .in("a")
    .out(stringBody)
    .serverLogic { _ => serviceA.read().map(_.asRight[Unit]) }

  private val readB = endpoint.get
    .in("b")
    .out(stringBody)
    .serverLogic { _ => serviceB.read().map(_.asRight[Unit]) }

  val all = List(readA, readB)
}
