package com.microservice.url.impl.persistence

import java.net.HttpURLConnection._
import java.time.LocalDateTime

import com.microservice.url.api.{UrlError, UrlStatus}
import com.microservice.url.impl.persistence.UrlState.{Alive, Dead}
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import org.scalatest.concurrent.Eventually.{eventually, interval, timeout}
import slick.dbio.{DBIO, DBIOAction, NoStream}
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcBackend

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * @author Yuriy Tumakha
  */
class UrlRepositorySpec extends FlatSpec {

  val urlRepository: UrlRepository = new UrlRepository {
    override val db: JdbcBackend#Database =
      Database.forURL(url = "jdbc:h2:mem:urlChecker;DB_CLOSE_DELAY=2", driver = "org.h2.Driver", keepAliveConnection = true)
  }
  val db = urlRepository.db
  val urls = UrlQueries.urls
  val ids = IdQueries.ids
  val urlIdMaps = UrlIdMapQueries.urlIdMaps
  val duration: Duration = 5 seconds

  val aliveUrl = "http://www.com/alive/url"
  val deadUrl = "http://www.com/dead/url"
  val removeUrl = "http://www.com/remove/url"
  val remove2Url = "http://www.com/remove/still/used/url"
  val url1 = "https://www.facebook.com"
  val url2 = "https://www.linkedin.com"
  val id1 = "A1B2C3"
  val id2 = "ABC"
  val removeId = "2DEL"

  private def run[R](future: Future[R]): R = {
    Await.result(future, duration)
    future.value.get.get
  }

  private def run[R](dbAction: DBIOAction[R, NoStream, Nothing]): R = run(db.run(dbAction))

  val createTestData: Unit =
    run(DBIO.sequence(Seq(
      urls.schema.create, ids.schema.create, urlIdMaps.schema.create,
      urls ++= Seq(
        UrlEntity(Some(1), aliveUrl, LocalDateTime.now, 0, Alive, HTTP_OK, "OK"),
        UrlEntity(Some(2), deadUrl, LocalDateTime.now, 0, Dead, HTTP_NOT_FOUND, "Not Found"),
        UrlEntity(Some(3), removeUrl, LocalDateTime.now, 0, Alive, HTTP_OK, "OK"),
        UrlEntity(Some(4), remove2Url, LocalDateTime.now, 0, Alive, HTTP_OK, "OK")
      ),
      ids ++= Seq(
        IdEntity(Some(1), "A10"),
        IdEntity(Some(2), "B20"),
        IdEntity(Some(3), removeId)
      ),
      urlIdMaps ++= Seq(
        UrlIdMap(1, 1),
        UrlIdMap(1, 2),
        UrlIdMap(3, 3),
        UrlIdMap(4, 1),
        UrlIdMap(4, 2)
      )
    )))

  def waitCondition(condition: => Unit): Unit =
    eventually(timeout(duration), interval(1 seconds)) {
      condition
    }

  "UrlRepository" should "register(URL, ID)" in {
    run(urlRepository.register(url1, id1))

    val urlEntity: UrlEntity = run(urls.filter(_.url === url1).result.head)
    urlEntity.id shouldNot be(None)
    urlEntity.url shouldBe url1
    urlEntity.state shouldBe UrlState.Alive
    urlEntity.code shouldBe HTTP_OK

    val idEntity: IdEntity = run(ids.filter(_.value === id1).result.head)
    idEntity.id shouldNot be(None)
    idEntity.value shouldBe id1

    val urlIdMap: UrlIdMap = run(urlIdMaps.filter(_.id === idEntity.id.get).result.head)
    urlIdMap.urlId shouldBe urlEntity.id.get
    urlIdMap.id shouldBe idEntity.id.get
  }

  it should "register(URL, ID) for the same url" in {
    run(urlRepository.register(url1, id2))

    val urlEntity: UrlEntity = run(urls.filter(_.url === url1).result.head)
    urlEntity.id shouldNot be(None)
    urlEntity.url shouldBe url1
    urlEntity.state shouldBe UrlState.Alive
    urlEntity.code shouldBe HTTP_OK

    val idEntity: IdEntity = run(ids.filter(_.value === id2).result.head)
    idEntity.id shouldNot be(None)
    idEntity.value shouldBe id2

    val urlIdMap: UrlIdMap = run(urlIdMaps.filter(_.id === idEntity.id.get).result.head)
    urlIdMap.urlId shouldBe urlEntity.id.get
    urlIdMap.id shouldBe idEntity.id.get
  }

  it should "register(URL, ID) for the same ID" in {
    run(urlRepository.register(url2, id1))

    val urlEntity: UrlEntity = run(urls.filter(_.url === url2).result.head)
    urlEntity.id shouldNot be(None)
    urlEntity.url shouldBe url2
    urlEntity.state shouldBe UrlState.Alive
    urlEntity.code shouldBe HTTP_OK

    val idEntity: IdEntity = run(ids.filter(_.value === id1).result.head)
    idEntity.id shouldNot be(None)
    idEntity.value shouldBe id1

    val urlIdMap: UrlIdMap = run(urlIdMaps.filter(_.urlId === urlEntity.id.get).result.head)
    urlIdMap.urlId shouldBe urlEntity.id.get
    urlIdMap.id shouldBe idEntity.id.get
  }

  it should "register(URL, ID) for the same URL and ID without exception" in {
    run(urlRepository.register(url1, id1))

    val urlEntity: UrlEntity = run(urls.filter(_.url === url1).result.head)
    urlEntity.id shouldNot be(None)
    urlEntity.url shouldBe url1
    urlEntity.state shouldBe UrlState.Alive
    urlEntity.code shouldBe HTTP_OK

    val idEntity: IdEntity = run(ids.filter(_.value === id1).result.head)
    idEntity.id shouldNot be(None)
    idEntity.value shouldBe id1

    printDB
  }

  it should "unregister(URL, ID)" in {
    run(urls.filter(_.url === removeUrl).result.headOption) shouldNot be(None)

    run(urlRepository.unregister(removeUrl, removeId))

    waitCondition {
      run(urls.filter(_.url === removeUrl).result.headOption) shouldBe None
    }
  }

  it should "unregister(URL, ID) if URL and ID are still used for other mappings" in {
    run(urls.filter(_.url === remove2Url).result.headOption) shouldNot be(None)

    run(urlRepository.unregister(remove2Url, "A10"))

    run(urls.filter(_.url === remove2Url).result.headOption) shouldNot be(None)
    run(ids.filter(_.value === "A10").result.headOption) shouldNot be(None)
  }

  it should "return UrlStatus for Alive URL" in {
    val urlStatus: UrlStatus = run(urlRepository.getStatus(aliveUrl))
    urlStatus.url shouldBe aliveUrl
    urlStatus.state shouldBe Alive.toString
    urlStatus.error shouldBe None
  }

  it should "return UrlStatus for Dead URL" in {
    val urlStatus: UrlStatus = run(urlRepository.getStatus(deadUrl))
    urlStatus.url shouldBe deadUrl
    urlStatus.state shouldBe Dead.toString
    urlStatus.error shouldBe Some(UrlError(HTTP_NOT_FOUND, "Not Found"))
  }

  it should "return ID list associated to URL" in {
    run(urlRepository.getIds(aliveUrl)) should contain only("A10", "B20")
  }

  it should "handle empty ID list" in {
    run(urlRepository.getIds(deadUrl)) shouldBe empty
  }

  private def printDB: Unit = {
    println(run(UrlQueries.urls.result))
    println(run(IdQueries.ids.result))
    println(run(UrlIdMapQueries.urlIdMaps.result))
  }

}
