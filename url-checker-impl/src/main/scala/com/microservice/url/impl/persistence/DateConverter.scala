package com.microservice.url.impl.persistence

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


/**
  * Convert date between java.time.* objects and String representation.
  *
  * @author Yuriy Tumakha
  */
object DateConverter {

  val timeFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  val toLocalDateTime: String => LocalDateTime = LocalDateTime.parse(_, timeFormatter)

  val fromLocalDateTime: LocalDateTime => String = _.format(timeFormatter)

}