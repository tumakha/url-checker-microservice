package com.microservice.url.impl.persistence

import java.time.Instant.ofEpochMilli
import java.time.LocalDateTime
import java.time.ZoneId.systemDefault
import java.time.format.DateTimeFormatter


/**
  * Convert date between java.time.* objects and [String|Long] representation.
  *
  * @author Yuriy Tumakha
  */
object DateConverter {

  val timeFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  val toLocalDateTime: String => LocalDateTime = LocalDateTime.parse(_, timeFormatter)

  val fromLocalDateTime: LocalDateTime => String = _.format(timeFormatter)

  def fromEpochMilli(epochMilli: Long): LocalDateTime =
    LocalDateTime.ofInstant(ofEpochMilli(epochMilli), systemDefault)

  def toEpochMilli(localDateTime: LocalDateTime): Long =
    localDateTime.atZone(systemDefault).toInstant.toEpochMilli

}