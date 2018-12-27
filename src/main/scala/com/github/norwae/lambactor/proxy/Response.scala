package com.github.norwae.lambactor.proxy

import java.nio.charset.StandardCharsets
import java.util.{Base64, Collections, List => JList, Map => JMap}

import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import com.typesafe.config.ConfigFactory

import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

object Response {
  val responseGenerationTimeout: FiniteDuration = ConfigFactory.load().
    getDuration("entity.creation.timeout").
    toMillis.
    millis
}

class Response(resp: HttpResponse)(implicit mat: Materializer) {
  private final val base64: Boolean = resp.entity.contentType.binary
  def getIsBase64Encoded: Boolean = base64 // helper for java bean detection
  @BeanProperty final val statusCode: Int = resp.status.intValue()
  @BeanProperty final val multiValueHeaders: JMap[String, JList[String]] = {
    val multiHeaders = resp.headers.groupBy(_.lowercaseName())
    val multiValueMap = multiHeaders.mapValues { headers ⇒
      headers.filter(_.renderInResponses).map(_.value()).asJava
    } + ("Content-Type" → Collections.singletonList(resp.entity.contentType.toString))

    multiValueMap.asJava
  }

  @BeanProperty final val body: String = {
    val contentBytes = Await.result(
      resp.entity.toStrict(Response.responseGenerationTimeout),
      Response.responseGenerationTimeout + 100.millis
    ).data.toArray

    if (base64) Base64.getEncoder.encodeToString(contentBytes)
    else new String(contentBytes, StandardCharsets.UTF_8)
  }
}
