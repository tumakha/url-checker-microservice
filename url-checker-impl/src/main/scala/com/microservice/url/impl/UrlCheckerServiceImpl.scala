package com.microservice.url.impl

import java.lang.Integer.MAX_VALUE
import java.net.URL

import akka.persistence.query.Sequence
import akka.stream.scaladsl.Source
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.microservice.url.api.UrlCheckerService.ID
import com.microservice.url.api._
import com.microservice.url.impl.persistence.DateConverter.toEpochMilli
import com.microservice.url.impl.persistence.{UrlEntity, UrlRepository}

import scala.concurrent.ExecutionContext

/**
  * Implementation of the UrlCheckerService.
  *
  * @author Yuriy Tumakha
  */
class UrlCheckerServiceImpl(urlRepository: UrlRepository, urlCheckScheduler: UrlCheckScheduler)
                           (implicit val ec: ExecutionContext)
  extends UrlCheckerService {

  override def register: ServiceCall[UrlIdPair, OkResponse] = ServiceCall {
    request =>
      urlRepository.register(new URL(request.url).toString, request.id).map(res => {
        urlRepository.findByUrl(request.url).map(urlCheckScheduler.checkUrl)
        OkResponse(res)
      })
  }

  override def unregister: ServiceCall[UrlIdPair, OkResponse] = ServiceCall {
    request =>
      urlRepository.unregister(request.url, request.id).map(counts =>
        OkResponse(s"Removed (${request.url}, ${request.id}). Deleted records: ${counts.sum}")
      )
  }

  override def getIds: ServiceCall[UrlWrapper, Seq[ID]] = ServiceCall {
    request => urlRepository.getIds(request.url)
  }

  override def getStatus: ServiceCall[UrlWrapper, UrlStatus] = ServiceCall {
    request => urlRepository.getStatus(request.url)
  }

  override def deadUrlTopic: Topic[DeadUrl] = {
    TopicProducer.singleStreamWithOffset { offset =>
      Source.fromPublisher(urlRepository.getLastDeadUrls(offset)).groupBy(MAX_VALUE, _._1)
        .map(t => (t._1, Seq(t._2)))
        .reduce((v1, v2) => (v1._1, v1._2 ++ v2._2))
        .map { case (k, v) =>
          (toDeadUrl(k, v), Sequence(toEpochMilli(k.lastCheckTime)))
        }
        .mergeSubstreams
    }
  }

  private def toDeadUrl(urlEntity: UrlEntity, idList: Seq[ID]): DeadUrl =
    DeadUrl(urlEntity.url, idList, UrlError(urlEntity.code, urlEntity.message))

}
