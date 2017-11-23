package com.microservice.url.impl

import java.util.concurrent.TimeUnit

import akka.actor.Status.Success
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import com.microservice.url.impl.actor.MasterRouter
import com.microservice.url.impl.persistence.{UrlEntity, UrlRepository}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Schedule check and recheck urls.
  * 
  * @author Yuriy Tumakha
  */
class UrlCheckScheduler(urlRepository: UrlRepository, system: ActorSystem)
                       (implicit val mat: Materializer, ec: ExecutionContext) {

  val router: ActorRef = system.actorOf(MasterRouter.props(urlRepository))

  val checkPeriod: FiniteDuration = system.settings.config
    .getDuration("url.checkPeriod", TimeUnit.MILLISECONDS).milliseconds

  val recheckPeriod: FiniteDuration = system.settings.config
    .getDuration("url.recheckPeriod", TimeUnit.MILLISECONDS).milliseconds

  implicit val t: Timeout = Timeout(10.seconds)

  system.scheduler.schedule(recheckPeriod, recheckPeriod) {
    startChecking()
  }

  def startChecking(): Unit = {
    val source = Source.fromPublisher(urlRepository.getUrls2Check(checkPeriod))
    source.runWith(Sink.actorRefWithAck(router, StartChecking, NextUrl, Success("Done")))
  }

  def checkUrl(urlEntity: UrlEntity): Unit = router ? urlEntity

}

case object StartChecking

case object NextUrl
