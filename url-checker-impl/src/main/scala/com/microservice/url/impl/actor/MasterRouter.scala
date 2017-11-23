package com.microservice.url.impl.actor

import akka.actor.Status.Success
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.routing._
import com.microservice.url.impl.StartChecking
import com.microservice.url.impl.persistence.{UrlEntity, UrlRepository}

/**
  * Router in front of UrlChecker actors.
  *
  * @author Yuriy Tumakha
  */
object MasterRouter {
  def props(urlRepository: UrlRepository): Props = Props(new MasterRouter(urlRepository))
}

class MasterRouter(urlRepository: UrlRepository) extends Actor with ActorLogging {

  val instances: Int = context.system.settings.config.getInt("url.checkerInstances")

  var router = {
    val routees = Vector.fill(instances)(ActorRefRoutee(createUrlChecker()))
    Router(SmallestMailboxRoutingLogic(), routees)
  }

  def receive = {
    case StartChecking =>
      log.info(s"URL Checking started by ${sender()}")
      router.routees foreach (_.send(StartChecking, sender()))
    case urlEntity: UrlEntity =>
      router.route(urlEntity, sender())
    case Success(msg) =>
      log.info(s"URL Checking: $msg")
    case Terminated(a) =>
      router = router.removeRoutee(a)
      router = router.addRoutee(createUrlChecker())
    case msg => log.warning(s"Received unknown message: $msg")
  }

  private def createUrlChecker(): ActorRef = {
    val r = context.actorOf(UrlChecker.props(urlRepository))
    context watch r
    r
  }

}