package com.microservice.url.api

import java.time.LocalDateTime

import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
import com.microservice.url.api.UrlCheckerService.ID
import play.api.libs.json.{Format, Json}

object UrlCheckerService {
  /**
    * ID associated with URL.
    */
  type ID = String

  val DEAD_URL_TOPIC = "dead-url"
}

/**
  * The url-checker service interface.
  * <p>
  * This describes everything that Lagom needs to know about how to serve and
  * consume the UrlCheckerService.
  * </p>
  */
trait UrlCheckerService extends Service {

  /**
    * Example: curl -H "Content-Type: application/json" -X POST -d '{"url":
    * "https://www.facebook.com/", "id": "ABC123"}' http://localhost:9000/api/url/register
    */
  def register: ServiceCall[UrlIdPair, OkResponse]

  /**
    * Example: curl -H "Content-Type: application/json" -X POST -d '{"url":
    * "https://www.facebook.com/", "id": "ABC123"}' http://localhost:9000/api/url/unregister
    */
  def unregister: ServiceCall[UrlIdPair, OkResponse]

  /**
    * Example: curl -H "Content-Type: application/json" -X POST -d '{"url":
    * "https://www.facebook.com/"}' http://localhost:9000/api/url/ids
    */
  def getIds: ServiceCall[UrlWrapper, Seq[ID]]

  /**
    * Example: curl -H "Content-Type: application/json" -X POST -d '{"url":
    * "https://www.facebook.com/"}' http://localhost:9000/api/url/status
    */
  def getStatus: ServiceCall[UrlWrapper, UrlStatus]

  /**
    * This gets published to Kafka.
    */
  def deadUrlTopic: Topic[DeadUrl]

  override final def descriptor = {
    import Service._
    // @formatter:off
    named("url-checker")
      .withCalls(
        pathCall("/api/url/register", register),
        pathCall("/api/url/unregister", unregister),
        pathCall("/api/url/ids", getIds),
        pathCall("/api/url/status", getStatus)
      )
      .withTopics(
        topic(UrlCheckerService.DEAD_URL_TOPIC, deadUrlTopic)
      )
      .withAutoAcl(true)
    // @formatter:on
  }
}

/**
  * URL-ID pair message class.
  */
case class UrlIdPair(url: String, id: ID)

object UrlIdPair {
  /**
    * Format for converting object to and from JSON.
    *
    * This will be picked up by a Lagom implicit conversion from Play's JSON format to Lagom's message serializer.
    */
  implicit val format: Format[UrlIdPair] = Json.format[UrlIdPair]
}

/**
  * URL wrapper message class.
  */
case class UrlWrapper(url: String)

object UrlWrapper {
  /**
    * Format for converting object to and from JSON.
    *
    * This will be picked up by a Lagom implicit conversion from Play's JSON format to Lagom's message serializer.
    */
  implicit val format: Format[UrlWrapper] = Json.format[UrlWrapper]
}

/**
  * Simple successful response message class.
  */
case class OkResponse(message: String)

object OkResponse {

  val OK: OkResponse = OkResponse("OK")

  implicit val format: Format[OkResponse] = Json.format[OkResponse]
}

/**
  * URL check status.
  */
case class UrlStatus(url: String, lastCheckTime: LocalDateTime, state: String, error: Option[UrlError])

object UrlStatus {
  implicit val format: Format[UrlStatus] = Json.format[UrlStatus]
}

/**
  * URL check error.
  */
case class UrlError(code: Int, message: String)

object UrlError {
  implicit val format: Format[UrlError] = Json.format[UrlError]
}

/**
  * The DeadUrl message class used by the topic stream.
  */
case class DeadUrl(url: String, associatedIds: Seq[ID], error: UrlError)

object DeadUrl {
  /**
    * Format for converting object to and from JSON.
    *
    * This will be picked up by a Lagom implicit conversion from Play's JSON format to Lagom's message serializer.
    */
  implicit val format: Format[DeadUrl] = Json.format[DeadUrl]
}
