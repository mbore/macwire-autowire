package com.softwaremill.macwire.autowire.database

import cats.effect.IO
import doobie.Transactor

class DbWriterA(xa: Transactor[IO]) {
  def writeIntoDbA(): IO[Unit]= IO {
    println("Write into DB A")
  }
}
