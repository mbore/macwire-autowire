package com.softwaremill.macwire.autowire.service

import cats.effect.IO
import com.softwaremill.macwire.autowire.database.{DbReaderB, DbWriterB}

class ServiceB(reader: DbReaderB, writer: DbWriterB) {
  def read(): IO[String] = reader.readFromDbB()
  def write(str: String): IO[Unit] = IO.println(s"WriteB [$str]") >> writer.writeIntoDbB()
}
