package io.codeheroes.microhero.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import org.json4s.DefaultFormats
import org.json4s.native.Serialization._

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._

case object ConsulMock {

  case class Request(ID: String, Name: String, Address: String, Port: Int, Check: Option[RequestCheck])

  case class RequestCheck(DeregisterCriticalServiceAfter: Option[String], HTTP: String, Interval: String)
  case class Service(Address: String, Port: Int)
  case class ResponseCheck(Status: String)
  case class Response(Service: Service, Checks: List[ResponseCheck])

}

class ConsulMock(host: String, port: Int)(implicit system: ActorSystem, mat: ActorMaterializer) {
  private implicit val formats = DefaultFormats
  private val _requests: ListBuffer[ConsulMock.Request] = ListBuffer.empty


  import ConsulMock._

  private val routes =
    (path("v1" / "agent" / "service" / "register") & put & extractStrictEntity(5 seconds)) { entity =>
      val body = read[Request](entity.data.utf8String)
      _requests += body

      body match {
        case b if b.Name == "invalidName" => complete(InternalServerError)
        case _ => complete(OK)
      }
    } ~
      (path("v1" / "health" / "service" / Segment) & get) {
        case "kafka" => complete(toResponse(OK, List(
          Response(Service("1.1.1.1", 9090), List(ResponseCheck("passing"))),
          Response(Service("2.2.2.2", 9090), List(ResponseCheck("passing"))),
          Response(Service("3.3.3.3", 9090), List(ResponseCheck("warning")))
        )))
        case other => complete(toResponse(OK, List.empty[ConsulMock.Response]))
      }

  Await.result(Http().bindAndHandle(routes, host, port), 5 seconds)

  def requests() = _requests.toList

  def clearRequests() = _requests.clear()

  private def toResponse(status: StatusCode, response: AnyRef): HttpResponse =
    HttpResponse(
      status = status,
      entity = HttpEntity(ContentTypes.`application/json`, write(response))
    )
}