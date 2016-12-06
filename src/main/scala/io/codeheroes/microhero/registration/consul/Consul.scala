package io.codeheroes.microhero.registration.consul

import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.{Done, NotUsed}
import org.json4s.DefaultFormats
import org.json4s.native.Serialization._

import scala.concurrent.Future
import scala.concurrent.duration._

sealed trait HealthCheck

case class HttpCheck(path: String, interval: FiniteDuration, ttl: FiniteDuration, deregisterAfter: Option[FiniteDuration]) extends HealthCheck

case class EndpointStatus(host: String, port: Int, healthy: Boolean)

class Consul(host: String, port: Int)(implicit system: ActorSystem) {
  private implicit val formats = DefaultFormats
  private implicit val mat = ActorMaterializer()
  private implicit val ec = system.dispatcher
  private val endpoint = Http()
  private val uri = s"http://$host:$port"

  import Consul._

  def registerService(host: String, port: Int, name: String, healthCheck: Option[HealthCheck] = None): Future[Done] = {
    val check = healthCheck.map {
      case HttpCheck(path, interval, ttl, deregister) => RequestCheck(deregister.map(toTime), Some(s"http://$host:$port$path"), toTime(interval))
    }
    val entity = Request(s"$name@$host:$port", name, host, port, check)
    val request = HttpRequest(method = HttpMethods.PUT,
      uri = s"$uri/v1/agent/service/register",
      entity = HttpEntity(ContentTypes.`application/json`, write(entity))
    )

    endpoint
      .singleRequest(request)
      .map(_.status)
      .map {
        case StatusCodes.OK => Done
        case other =>
          throw new IllegalStateException(s"Registration of service $name@$host:$port failed with response status $other")
      }
  }


  def getServices(name: String): Future[List[EndpointStatus]] = {
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$uri/v1/health/service/$name"
    )

    endpoint
      .singleRequest(request)
      .flatMap(r => r.entity.toStrict(30 seconds).map(_.data.utf8String).map(body => (r.status, body)))
      .map {
        case (StatusCodes.OK, body) =>
          read[List[Response]](body)
            .map(r => EndpointStatus(r.Service.Address, r.Service.Port, r.Checks.forall(_.Status == "passing")))
        case (StatusCodes.NotFound, _) =>
          List.empty
        case (other, _) =>
          throw new IllegalStateException(s"Loading services for $name failed with response status $other")
      }
  }


  def streamServices(name: String, interval: FiniteDuration): Source[EndpointStatus, Cancellable] =
    Source
      .tick(0 seconds, interval, NotUsed)
      .mapAsync(1)(_ => getServices(name)).mapConcat(identity)


  private def toTime(duration: FiniteDuration) = s"${duration.toSeconds}s"
}

object Consul {

  private case class Request(ID: String, Name: String, Address: String, Port: Int, Check: Option[RequestCheck])

  private case class RequestCheck(DeregisterCriticalServiceAfter: Option[String], HTTP: Option[String], Interval: String)

  private case class Service(Address: String, Port: Int)

  private case class ResponseCheck(Status: String)

  private case class Response(Service: Service, Checks: List[ResponseCheck])

}