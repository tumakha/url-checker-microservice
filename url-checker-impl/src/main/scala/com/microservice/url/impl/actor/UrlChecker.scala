package com.microservice.url.impl.actor

import java.net.HttpURLConnection.HTTP_OK
import java.net.{HttpURLConnection, URL}
import java.time.LocalDateTime

import akka.actor.{Actor, ActorLogging, Props}
import com.microservice.url.impl.persistence.UrlState.Alive
import com.microservice.url.impl.persistence.{UrlEntity, UrlRepository, UrlState}
import com.microservice.url.impl.{NextUrl, StartChecking}

/**
  * URL Checker Akka Actor.
  *
  * @author Yuriy Tumakha
  */
object UrlChecker {
  def props(urlRepository: UrlRepository): Props = Props(new UrlChecker(urlRepository))
}

class UrlChecker(urlRepository: UrlRepository) extends Actor with ActorLogging {

  val maxAttempts: Int = context.system.settings.config.getInt("url.recheckThreshold")

  override def receive = {
    case StartChecking =>
      sender() ! NextUrl
    case urlEntity: UrlEntity =>
      log.debug(s"Check $urlEntity")
      urlRepository.update(setHttpCheckResult(urlEntity))
      sender() ! NextUrl
    case msg => log.warning(s"Received unknown message: $msg")
  }

  def setHttpCheckResult(u: UrlEntity): UrlEntity = {
    val (code, msg) = checkUrl(u.url)
    var state = Alive
    var attempts = 0
    if (code != HTTP_OK) {
      attempts = u.attempts + 1
      if (attempts < maxAttempts)
        state = UrlState.PotentiallyDead
      else
        state = UrlState.Dead
    }
    UrlEntity(u.id, u.url, LocalDateTime.now, attempts, state, code, msg)
  }


  def checkUrl(url: String): (Int, String) = {
    val connection: HttpURLConnection = new URL(url).openConnection.asInstanceOf[HttpURLConnection]
    (connection.getResponseCode, connection.getResponseMessage)
  }

}
