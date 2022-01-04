package com.softwaremill.macwire.autowire.database

import cats.effect.IO
import doobie.Transactor

class DbWriterB(xa: Transactor[IO]) {
  def writeIntoDbB(): IO[Unit] = IO {
    println("Write into DB B")
  }
}
