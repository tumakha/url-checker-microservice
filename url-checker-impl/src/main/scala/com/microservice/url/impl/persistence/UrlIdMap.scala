package com.microservice.url.impl.persistence

import slick.jdbc.PostgresProfile.api._

/**
  * Slick "url_id_map" table configuration.
  *
  * @author Yuriy Tumakha
  */
case class UrlIdMap(urlId: Long, id: Long)

class UrlIdMapTable(tag: Tag) extends Table[UrlIdMap](tag, "url_id_map") {
  def urlId = column[Long]("url_id")

  def id = column[Long]("id")

  // Every table needs a * projection with the same type as the table's type parameter
  def * = (urlId, id) <> (UrlIdMap.tupled, UrlIdMap.unapply)

  def pk = primaryKey("pk_url_id", (urlId, id))

  def urlFk = foreignKey("url_fk", urlId, UrlQueries.urls)(url => url.id)

  def idFk = foreignKey("id_fk", id, IdQueries.ids)(id => id.id)
}

object UrlIdMapQueries {
  lazy val urlIdMaps = TableQuery[UrlIdMapTable]
}
