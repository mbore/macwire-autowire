package com.softwaremill.macwire.autowire.database

import cats.effect.IO
import doobie.Transactor

class DbReaderB(xa: Transactor[IO]) {
  def readFromDbB(): IO[String]= IO { "DB_B" }
}
