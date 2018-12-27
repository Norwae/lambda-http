package com.github.norwae.lambactor.proxy

import java.util.{Base64, Collections, List => JList, Map => JMap}

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.util.ByteString
import com.github.norwae.lambactor.proxy

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

/**
  * Models a API gateway request. As per
  * [[https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-input-format
  * AWS documentation]]:
  *
  * {{{
  * {
  * "resource": "Resource path",
  * "path": "Path parameter",
  * "httpMethod": "Incoming request's method name"
  * "headers": {String containing incoming request headers}
  * "multiValueHeaders": {List of strings containing incoming request headers}
  * "queryStringParameters": {query string parameters }
  * "multiValueQueryStringParameters": {List of query string parameters}
  * "pathParameters":  {path parameters}
  * "stageVariables": {Applicable stage variables}
  * "requestContext": {Request context, including authorizer-returned key-value pairs}
  * "body": "A JSON string of the request payload."
  * "isBase64Encoded": "A boolean flag to indicate if the applicable request payload is Base64-encode"
  * }`
  * }}}
  */
class Request {
  @BeanProperty var path: String = _
  @BeanProperty var httpMethod: String = _
  @BeanProperty var multiValueHeaders: JMap[String, JList[String]] = _
  @BeanProperty var headers: JMap[String, String] = _
  @BeanProperty var pathParameters: JMap[String, String] = _
  @BeanProperty var stageVariables: JMap[String, String] = _
  @BeanProperty var body: String = _
  @BeanProperty var isBase64Encoded: Boolean = _

  private def httpHeaders = {
    val pathParameters = Option(this.pathParameters).getOrElse(Collections.emptyMap())
    val stageVariables = Option(this.stageVariables).getOrElse(Collections.emptyMap())
    val simpleHeaders = Option(this.headers).getOrElse(Collections.emptyMap())
    val multiValueHeaders = Option(this.multiValueHeaders).getOrElse(Collections.emptyMap())

    val pathHeaders = pathParameters.asScala.map(proxy.PathParameter.tupled)
    val stageHeaders = stageVariables.asScala.map(proxy.StageVariable.tupled)
    val headersRaw =
      simpleHeaders.asScala.mapValues(List.apply(_)) ++
      multiValueHeaders.asScala.mapValues(_.asScala.toSeq)

    val parsedHeaders = (for {
      (name, values) ← headersRaw
      value ← values
      successfulParses ← HttpHeader.parse(name, value) match {
        case ParsingResult.Ok(header, _) ⇒ Seq(header)
        case _ ⇒ Nil
      }
    } yield successfulParses).toSet


    List.concat(parsedHeaders, stageHeaders, pathHeaders)
  }

  def asHttpRequest: HttpRequest = {
    val body = Option(this.body) getOrElse ""
    val path = Option(this.path) getOrElse "/"
    val httpMethod = Option(this.httpMethod) getOrElse "MISSING"
    val entityBytes =
      if (!isBase64Encoded) ByteString(body)
      else ByteString(Base64.getDecoder.decode(body))

    val headers = httpHeaders
    val contentType = headers.collectFirst {
      case `Content-Type`(ct) ⇒ ct
    } getOrElse ContentTypes.`application/octet-stream`

    HttpRequest(
      HttpMethods.getForKeyCaseInsensitive(httpMethod) getOrElse HttpMethod.custom(httpMethod, false, false, RequestEntityAcceptance.Tolerated),
      Uri(path),
      headers,
      HttpEntity.Strict(contentType, entityBytes)
    )
  }
}
