package com.softwaremill.macwire.autowire.service

import cats.effect.IO
import com.softwaremill.macwire.autowire.database.{DbReaderA, DbWriterA}

class ServiceA(reader: DbReaderA, writer: DbWriterA) {
  def read(): IO[String] = reader.readFromDbA()
  def write(str: String): IO[Unit] = IO.println(s"WriteA [$str]") >> writer.writeIntoDbA()
}
