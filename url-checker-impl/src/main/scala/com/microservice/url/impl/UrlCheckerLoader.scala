package com.microservice.url.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.microservice.url.api.{DeadUrl, UrlCheckerService}
import com.microservice.url.impl.persistence.UrlRepository
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}

import scala.collection.immutable.Seq

/**
  * Application loader.
  *
  * @author Yuriy Tumakha
  */
class UrlCheckerLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new UrlCheckerApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new UrlCheckerApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[UrlCheckerService])
}

abstract class UrlCheckerApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with CassandraPersistenceComponents
    with LagomKafkaComponents
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer = serverFor[UrlCheckerService](wire[UrlCheckerServiceImpl])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry = UrlCheckerSerializerRegistry

  lazy val urlRepository = wire[UrlRepository]

  val urlCheckScheduler = wire[UrlCheckScheduler]

}

/**
  * Akka serialization, used by both persistence and remoting, needs to have
  * serializers registered for every type serialized or deserialized. While it's
  * possible to use any serializer you want for Akka messages, out of the box
  * Lagom provides support for JSON, via this registry abstraction.
  *
  * The serializers are registered here, and then provided to Lagom in the
  * application loader.
  */
object UrlCheckerSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[DeadUrl]
  )
}