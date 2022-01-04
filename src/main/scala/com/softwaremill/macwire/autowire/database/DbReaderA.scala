package com.softwaremill.macwire.autowire.database

import cats.effect.IO
import doobie.Transactor

class DbReaderA(xa: Transactor[IO]) {
  def readFromDbA(): IO[String]= IO { "DB_A" }
}
