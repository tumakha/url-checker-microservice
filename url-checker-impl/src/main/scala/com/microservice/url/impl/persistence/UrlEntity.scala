package com.microservice.url.impl.persistence

import java.time.LocalDateTime

import com.microservice.url.impl.persistence.DateConverter.{fromLocalDateTime, toLocalDateTime}
import com.microservice.url.impl.persistence.UrlQueries._
import com.microservice.url.impl.persistence.UrlState.{Alive, UrlState}
import slick.jdbc.JdbcType
import slick.jdbc.PostgresProfile.api._

/**
  * Slick "url" table configuration.
  *
  * @author Yuriy Tumakha
  */
case class UrlEntity(id: Option[Long], url: String, lastCheckTime: LocalDateTime, attempts: Int,
                     state: UrlState, code: Int, message: String)

object UrlState extends Enumeration {
  type UrlState = Value
  val Alive, PotentiallyDead, Dead = Value
}

class UrlTable(tag: Tag) extends Table[UrlEntity](tag, "url") {

  def id = column[Long]("url_id", O.PrimaryKey, O.AutoInc)

  def url = column[String]("url")

  def url_idx = index("idx_url", url, unique = true)

  def lastCheckTime = column[LocalDateTime]("last_check_time")

  def attempts = column[Int]("attempts", O.Default(0))

  def state = column[UrlState]("state", O.Default(Alive))

  def httpCode = column[Int]("http_code", O.Default(200))

  def httpMessage = column[String]("http_message", O.Default("OK"))

  // Every table needs a * projection with the same type as the table's type parameter
  def * = (id.?, url, lastCheckTime, attempts, state, httpCode, httpMessage) <> (UrlEntity.tupled, UrlEntity.unapply)

  def getIds = UrlIdMapQueries.urlIdMaps.filter(_.urlId === id).flatMap(_.idFk)

}

object UrlQueries {

  // UrlState custom type mapper
  implicit val stateMapper: JdbcType[UrlState] = MappedColumnType.base(_.toString, UrlState.withName)

  // LocalDateTime custom type mapper
  implicit val timeMapper: JdbcType[LocalDateTime] = MappedColumnType.base(fromLocalDateTime, toLocalDateTime)

  val urls = TableQuery[UrlTable]

  def findByUrl(url: String) = Compiled(urls.filter(_.url === url))

}
