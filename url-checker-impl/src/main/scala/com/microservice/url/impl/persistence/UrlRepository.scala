package com.microservice.url.impl.persistence

import java.net.HttpURLConnection.HTTP_OK
import java.time.{LocalDate, LocalDateTime, LocalTime}

import com.lightbend.lagom.scaladsl.api.transport.NotFound
import com.microservice.url.api.UrlCheckerService.ID
import com.microservice.url.api.{DeadUrl, UrlError, UrlStatus}
import com.microservice.url.impl.persistence.UrlQueries._
import com.microservice.url.impl.persistence.UrlState.{Alive, PotentiallyDead}
import slick.basic.DatabasePublisher
import slick.dbio.Effect.Write
import slick.dbio.{DBIO, DBIOAction, NoStream, Streaming}
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api._

import scala.collection.{immutable, mutable}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * URL entities repository.
  *
  * @author Yuriy Tumakha
  */
class UrlRepository(implicit ec: ExecutionContext) {
  val db: JdbcBackend#Database = Database.forConfig("database")
  val minDateTime: LocalDateTime = LocalDateTime.of(LocalDate.of(1900, 1, 1), LocalTime.MIN)
  val ids = IdQueries.ids
  val urlIdMaps = UrlIdMapQueries.urlIdMaps

  /**
    * Insert new entity in "url" and "id" table if not exists.
    * and create mapping between url and id in table "url_id_map"
    */
  def register(url: String, id: ID): Future[String] =
    run(
      (for {
        u <- urls.filter(_.url === url).result.headOption
        _ <- urls ++= newUrlEntity(url: String, u.isEmpty)
        urlKey <- urls.filter(_.url === url).result.head.map(_.id.get)
        i <- ids.filter(_.value === id).result.headOption
        _ <- ids ++= newIdEntity(id, i.isEmpty)
        idKey <- ids.filter(_.value === id).result.head.map(_.id.get)
        ui <- urlIdMaps.filter(e => e.urlId === urlKey && e.id === idKey).result.headOption
        _ <- urlIdMaps ++= newUrlIdMap(urlKey, idKey, ui.isEmpty)
      } yield if (ui.isEmpty) "OK" else "Already registered").transactionally
    )

  private def newUrlEntity(url: String, create: Boolean): Seq[UrlEntity] =
    if (create) Seq(UrlEntity(None, url, minDateTime, 0, Alive, HTTP_OK, ""))
    else Seq()

  private def newIdEntity(id: ID, create: Boolean): Seq[IdEntity] =
    if (create) Seq(IdEntity(None, id))
    else Seq()

  private def newUrlIdMap(urlKey: Long, idKey: Long, create: Boolean): Seq[UrlIdMap] =
    if (create) Seq(UrlIdMap(urlKey, idKey))
    else Seq()

  /**
    * Delete mapping between url and id in table "url_id_map".
    * Delete entities in "url" and "id" table if they are not used in "url_id_map".
    */
  def unregister(url: String, id: ID): Future[Seq[Int]] =
    run(
      (for {
        u <- urls.filter(_.url === url).result.headOption
        i <- ids.filter(_.value === id).result.headOption
        res <- DBIO.sequence(deleteActions(u, i))
      } yield res).transactionally
    )

  private def deleteActions(urlEntity: Option[UrlEntity], idEntity: Option[IdEntity]): Seq[DBIOAction[Int, NoStream, Write]] = {
    val dbActions: mutable.Buffer[DBIOAction[Int, NoStream, Write]] = mutable.Buffer.empty
    for {
      u <- urlEntity
      urlKey <- u.id
      i <- idEntity
      idKey <- i.id
    } yield dbActions += urlIdMaps.filter(e => e.urlId === urlKey && e.id === idKey).delete

    urlEntity.map(u => dbActions += urls.filter(_.url === u.url
      && urlIdMaps.filter(_.urlId === u.id).length === 0).delete)

    idEntity.map(i => dbActions += ids.filter(_.value === i.value
      && urlIdMaps.filter(_.id === i.id).length === 0).delete)

    dbActions
  }

  /**
    * Inner join of 3 tables "url", "id" and "url_id_map" to get IDs associated with url.
    */
  def getIds(url: String): Future[Seq[ID]] = {
    val join = for {
      u <- urls if u.url === url
      ui <- urlIdMaps if ui.urlId === u.id
      i <- ids if i.id === ui.id
    } yield i.value
    run(join.result)
  }

  /**
    * Returns entity from "url" table or throw NotFound exception.
    */
  def findByUrl(url: String): Future[UrlEntity] =
    run(UrlQueries.findByUrl(url).result.headOption.map {
      case Some(urlEntity) => urlEntity
      case None => throw NotFound(s"Url $url is not registered")
    })

  /**
    * Get url check status from "url" table or throw NotFound exception.
    */
  def getStatus(url: String): Future[UrlStatus] = findByUrl(url).map(toUrlStatus)

  private def toUrlStatus(url: UrlEntity): UrlStatus =
    UrlStatus(url.url, url.lastCheckTime, url.state.toString, error(url.code, url.message))

  private def error(code: Int, msg: String): Option[UrlError] =
    if (code == HTTP_OK) None
    else Some(UrlError(code, msg))

  /**
    * Return Alive urls for next Check or PotentiallyDead for Recheck.
    */
  def getUrls2Check(checkPeriod: FiniteDuration): DatabasePublisher[UrlEntity] = {
    val lastCheckTime = LocalDateTime.now.minusSeconds(checkPeriod.toSeconds - 5)
    stream(urls.filter(u => u.state === PotentiallyDead ||
      (u.state === Alive && u.lastCheckTime < lastCheckTime)).result)
  }

  /**
    * Get last dead urls for sending to Kafka Topic.
    */
  def getLastDeadUrls: immutable.Iterable[DeadUrl] = {
    val lastSendTime = LocalDateTime.now.minusSeconds(33)
    val deadUrls = for {
      u <- urls if u.state === UrlState.Dead && u.lastCheckTime > lastSendTime
      ui <- urlIdMaps if ui.urlId === u.id
      i <- ids if i.id === ui.id
    } yield (u, i.value)
    Await.result(run(deadUrls.result.map {
      _.groupBy(_._1).map { case (k, v) => toDeadUrl(k, v.map(_._2)) }
    }), 10.seconds)
  }

  private def toDeadUrl(urlEntity: UrlEntity, idList: Seq[String]): DeadUrl =
    DeadUrl(urlEntity.url, idList, UrlError(urlEntity.code, urlEntity.message))

  /**
    * Save UrlEntity to database.
    */
  def update(urlEntity: UrlEntity): Future[Int] = run(urls.insertOrUpdate(urlEntity))

  /**
    * Run database query.
    */
  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = db.run(a)

  /**
    * Streaming entities from database.
    */
  def stream[T](a: DBIOAction[_, Streaming[T], Nothing]): DatabasePublisher[T] = db.stream(a)

  /**
    * Close DB on application exit.
    */
  def closeDb: Unit = db.close

}
