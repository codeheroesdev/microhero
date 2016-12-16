package io.codeheroes.microhero

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import io.codeheroes.microhero.core.ConsulMock
import io.codeheroes.microhero.registration.consul.{Consul, EndpointStatus, HttpCheck}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Success

class ConsulTests extends FlatSpec with Matchers {
  private implicit val system = ActorSystem()
  private implicit val mat = ActorMaterializer()
  private implicit val ec = system.dispatcher
  private val mockHost = "localhost"
  private val mockPort = 31000
  private val mock = new ConsulMock(mockHost, mockPort)


  "Consul registerService" should "should register service" in {
    mock.clearRequests()
    val consul = new Consul(mockHost, mockPort)

    val result = await(consul.registerService("192.168.1.1", 9092, "kafka", Some(HttpCheck("/status", 30 seconds, Some(90 seconds)))))
    val requests = mock.requests()

    result shouldBe Success(Done)
    requests should have size 1
    requests.head.ID shouldBe s"kafka@192.168.1.1:9092"
    requests.head.Address shouldBe "192.168.1.1"
    requests.head.Port shouldBe 9092
    requests.head.Name shouldBe "kafka"
    requests.head.Check.map(_.HTTP) shouldBe Some("http://192.168.1.1:9092/status")
    requests.head.Check.map(_.Interval) shouldBe Some("30s")
    requests.head.Check.flatMap(_.DeregisterCriticalServiceAfter) shouldBe Some("90s")
    println(requests)
  }

  it should "fail if host is not reachable" in {
    val consul = new Consul("localhost", mockPort + 1000)
    val result = await(consul.registerService("192.168.1.1", 9092, "kafka"))
    result.isFailure shouldBe true
  }

  it should "fail if name is invalid" in {
    mock.clearRequests()
    val consul = new Consul(mockHost, mockPort)

    val result = await(consul.registerService("192.168.1.1", 9092, "invalidName"))

    result.isFailure shouldBe true
  }

  it should "not register health check if not provided" in {
    mock.clearRequests()
    val consul = new Consul(mockHost, mockPort)

    val result = await(consul.registerService("192.168.1.1", 9092, "kafka"))
    val requests = mock.requests()

    result shouldBe Success(Done)
    requests should have size 1
    requests.head.Check.isEmpty shouldBe true
  }

  "Consul getServices" should "return list of current services" in {
    mock.clearRequests()
    val consul = new Consul(mockHost, mockPort)

    val result = await(consul.getServices("kafka"))

    result.isSuccess shouldBe true
    result.get should contain allOf(
      EndpointStatus("1.1.1.1", 9090, healthy = true),
      EndpointStatus("2.2.2.2", 9090, healthy = true),
      EndpointStatus("3.3.3.3", 9090, healthy = false)
      )
  }

  it should "fail if host is not reachable" in {
    val consul = new Consul(mockHost, mockPort + 1000)
    val result = await(consul.getServices("kafka"))
    result.isFailure shouldBe true
  }

  it should "return empty list if service not exists" in {
    mock.clearRequests()
    val consul = new Consul(mockHost, mockPort)

    val result = await(consul.getServices("zookeeper"))

    result.isSuccess shouldBe true
    result.get should have size 0
  }

  private def await[T](f: Future[T]) = Await.ready(f, 5 seconds).value.get

}