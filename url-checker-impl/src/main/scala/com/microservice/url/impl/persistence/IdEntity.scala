package com.microservice.url.impl.persistence

import slick.jdbc.PostgresProfile.api._

/**
  * Slick "id" table configuration.
  *
  * @author Yuriy Tumakha
  */
case class IdEntity(id: Option[Long], value: String)

class IdTable(tag: Tag) extends Table[IdEntity](tag, "id") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

  def value = column[String]("value")

  // value unique index
  def value_idx = index("idx_value", value, unique = true)

  // Every table needs a * projection with the same type as the table's type parameter
  def * = (id.?, value) <> (IdEntity.tupled, IdEntity.unapply)

}

object IdQueries {
  lazy val ids = TableQuery[IdTable]
}
