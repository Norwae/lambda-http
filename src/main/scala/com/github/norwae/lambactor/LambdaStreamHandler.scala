package com.github.norwae.lambactor

import java.io.{ByteArrayOutputStream, PrintStream, PrintWriter}

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, SourceQueue, SourceQueueWithComplete, Zip}
import akka.stream._
import akka.util.ByteString
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

object LambdaStreamHandler {
  implicit val materializer: Materializer = {
    implicit val sys: ActorSystem = ActorSystem()
    ActorMaterializer()
  }
}


abstract class LambdaStreamHandler extends RequestHandler[proxy.Request, proxy.Response] {
  def handleFlow: Flow[HttpRequest, HttpResponse, Any]
  import LambdaStreamHandler.materializer
  import materializer.executionContext

  private def runQueued(queueInterface: SourceQueue[(HttpRequest, Promise[HttpResponse])])(in: HttpRequest) = {
    val promise = Promise[HttpResponse]
    for {
      submission ← queueInterface.offer(in, promise)
      if submission == QueueOfferResult.enqueued
      data ← promise.future
    } yield data
  }

  private val runRequest = {
    RunnableGraph.fromGraph({
      type In = (HttpRequest, Promise[HttpResponse])
      val in = Source.queue[In](0, OverflowStrategy.fail).mapMaterializedValue(runQueued(_))

      GraphDSL.create(in) { implicit b ⇒ in ⇒
        import GraphDSL.Implicits._
        val out = b add Sink.foreach[(HttpResponse, Promise[HttpResponse])] {
          case (obj, promise) ⇒ promise.success(obj)
        }
        val handler = b add handleFlow
        val split = b add Broadcast[In](2)
        val requestOnly = b add Flow[In].map(_._1)
        val promiseOnly = b add Flow[In].map(_._2)
        val zip = b add Zip[HttpResponse, Promise[HttpResponse]]

        in    ~> split ~> requestOnly ~> handler ~> zip.in0
                 split ~> promiseOnly            ~> zip.in1
                                                    zip.out ~> out

        ClosedShape
      }
    }).run()
  }

  override def handleRequest(input: proxy.Request, context: Context): proxy.Response = {
    val response = try {
      val request = input.asHttpRequest
      Await.result(runRequest(request), Duration.Inf)
    } catch {
      case NonFatal(e) ⇒
        e.printStackTrace()
        val entityBytes = ByteString("An internal error has occurred. See the log for details.")
        HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, entityBytes))
    }

    new proxy.Response(response)
  }

}
