/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.discovery.awsapi.ec2

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{ DescribeInstancesRequest, DescribeInstancesResult, Instance, Reservation }
import com.typesafe.config.ConfigFactory
import org.apache.pekko
import pekko.actor.{ ActorSystem, ExtendedActorSystem }
import pekko.discovery.Lookup
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Millis, Seconds, Span }

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class Ec2TagBasedServiceDiscoveryTest extends AnyFunSuite with Matchers with BeforeAndAfterAll with ScalaFutures {

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  private val system = ActorSystem(
    "Ec2TagBasedServiceDiscoveryTest",
    ConfigFactory.parseString("""
      pekko.actor.provider = local
    """).withFallback(ConfigFactory.load()))

  private var systemWithPortsOpt: Option[ActorSystem] = None

  private def systemWithPorts: ActorSystem = {
    val sys = ActorSystem(
      "Ec2TagBasedServiceDiscoveryTestPorts",
      ConfigFactory.parseString("""
        pekko.actor.provider = local
        pekko.discovery.aws-api-ec2-tag-based.ports = [8558, 2552]
      """).withFallback(ConfigFactory.load()))
    systemWithPortsOpt = Some(sys)
    sys
  }

  override def afterAll(): Unit = {
    systemWithPortsOpt.foreach(_.terminate())
    system.terminate()
    super.afterAll()
  }

  private def extSystem = system.asInstanceOf[ExtendedActorSystem]

  private def makeResult(ips: List[String], nextToken: Option[String] = None): DescribeInstancesResult = {
    val instances = ips.map { ip =>
      val instance = new Instance()
      instance.setPrivateIpAddress(ip)
      instance
    }
    val reservation = new Reservation()
    reservation.setInstances(instances.asJava)
    val result = new DescribeInstancesResult()
    result.setReservations(List(reservation).asJava)
    nextToken.foreach(result.setNextToken)
    result
  }

  test("returns empty list when no instances are found") {
    val ec2Client = mock(classOf[AmazonEC2])
    val emptyResult = new DescribeInstancesResult()
    emptyResult.setReservations(List.empty[Reservation].asJava)
    when(ec2Client.describeInstances(any(classOf[DescribeInstancesRequest]))).thenReturn(emptyResult)

    val discovery = new Ec2TagBasedServiceDiscovery(extSystem, ec2Client)
    val resolved = discovery.lookup(Lookup("my-service"), 5.seconds).futureValue

    resolved.serviceName should be("my-service")
    resolved.addresses should be(empty)
  }

  test("returns resolved targets for discovered instances") {
    val ec2Client = mock(classOf[AmazonEC2])
    when(ec2Client.describeInstances(any(classOf[DescribeInstancesRequest])))
      .thenReturn(makeResult(List("10.0.0.1", "10.0.0.2")))

    val discovery = new Ec2TagBasedServiceDiscovery(extSystem, ec2Client)
    val resolved = discovery.lookup(Lookup("my-service"), 5.seconds).futureValue

    resolved.serviceName should be("my-service")
    resolved.addresses should have size 2
    resolved.addresses.map(_.host) should contain allOf ("10.0.0.1", "10.0.0.2")
    resolved.addresses.foreach(_.port should be(None))
  }

  test("handles pagination by following nextToken") {
    val ec2Client = mock(classOf[AmazonEC2])
    when(ec2Client.describeInstances(any(classOf[DescribeInstancesRequest])))
      .thenReturn(makeResult(List("10.0.0.1"), nextToken = Some("page2")))
      .thenReturn(makeResult(List("10.0.0.2")))

    val discovery = new Ec2TagBasedServiceDiscovery(extSystem, ec2Client)
    val resolved = discovery.lookup(Lookup("my-service"), 5.seconds).futureValue

    resolved.addresses should have size 2
    resolved.addresses.map(_.host) should contain allOf ("10.0.0.1", "10.0.0.2")
    verify(ec2Client, times(2)).describeInstances(any(classOf[DescribeInstancesRequest]))
  }

  test("returns multiple resolved targets per instance when ports are configured") {
    val ec2Client = mock(classOf[AmazonEC2])
    when(ec2Client.describeInstances(any(classOf[DescribeInstancesRequest])))
      .thenReturn(makeResult(List("10.0.0.1")))

    val discovery =
      new Ec2TagBasedServiceDiscovery(systemWithPorts.asInstanceOf[ExtendedActorSystem], ec2Client)
    val resolved = discovery.lookup(Lookup("my-service"), 5.seconds).futureValue

    resolved.addresses should have size 2
    resolved.addresses.map(_.host) should contain only "10.0.0.1"
    resolved.addresses.map(_.port) should contain allOf (Some(8558), Some(2552))
  }

  test("applies tag-key filter using the service name from the lookup") {
    val ec2Client = mock(classOf[AmazonEC2])
    when(ec2Client.describeInstances(any(classOf[DescribeInstancesRequest])))
      .thenReturn(makeResult(List("10.0.0.5")))

    val discovery = new Ec2TagBasedServiceDiscovery(extSystem, ec2Client)
    val resolved = discovery.lookup(Lookup("tagged-service"), 5.seconds).futureValue

    resolved.serviceName should be("tagged-service")
    resolved.addresses should have size 1
    resolved.addresses.head.host should be("10.0.0.5")

    val captor = ArgumentCaptor.forClass(classOf[DescribeInstancesRequest])
    verify(ec2Client).describeInstances(captor.capture())
    val filters = captor.getValue.getFilters.asScala
    filters.exists(f => f.getName == "tag:service" && f.getValues.asScala.contains("tagged-service")) should be(true)
  }
}
