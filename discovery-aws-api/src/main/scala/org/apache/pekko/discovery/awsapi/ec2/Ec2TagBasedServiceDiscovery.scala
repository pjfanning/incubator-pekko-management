/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.discovery.awsapi.ec2

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.retries.DefaultRetryStrategy
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.{ DescribeInstancesRequest, Filter }
import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.annotation.InternalApi
import pekko.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import pekko.discovery.awsapi.ec2.Ec2TagBasedServiceDiscovery.parseFiltersString
import pekko.discovery.{ Lookup, ServiceDiscovery }
import pekko.event.Logging
import pekko.pattern.after

import java.net.{ InetAddress, URI }
import java.util.concurrent.TimeoutException
import scala.annotation.tailrec
import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.CollectionConverters._
import scala.util.Try

/** INTERNAL API */
@InternalApi private[ec2] object Ec2TagBasedServiceDiscovery {

  private[ec2] def parseFiltersString(filtersString: String): List[Filter] =
    filtersString
      .split(";")
      .filter(_.nonEmpty)
      .map(kv => kv.split("="))
      .toList
      .map(kv => {
        assert(kv.length == 2, "failed to parse one of the key-value pairs in filters")
        Filter.builder().name(kv(0)).values(kv(1)).build()
      })

}

final class Ec2TagBasedServiceDiscovery(system: ExtendedActorSystem) extends ServiceDiscovery {

  private val log = Logging(system, classOf[Ec2TagBasedServiceDiscovery])

  private implicit val ec: ExecutionContext = system.dispatchers.lookup("pekko.actor.default-blocking-io-dispatcher")

  private val config = system.settings.config.getConfig("pekko.discovery.aws-api-ec2-tag-based")

  private val tagKey = config.getString("tag-key")

  private val otherFiltersString = config.getString("filters")
  private val otherFilters = parseFiltersString(otherFiltersString)

  private val preDefinedPorts =
    config.getIntList("ports").asScala.toList match {
      case Nil  => None
      case list => Some(list) // Pekko Management ports
    }

  private val runningInstancesFilter = Filter.builder().name("instance-state-name").values("running").build()

  private val ec2Client: Ec2Client = {
    // we have our own retry/back-off mechanism (in Cluster Bootstrap), so we don't need EC2Client's in addition
    val overrideConfig =
      ClientOverrideConfiguration.builder().retryStrategy(DefaultRetryStrategy.doNotRetry()).build()
    val builder = Ec2Client.builder().overrideConfiguration(overrideConfig)

    if (config.hasPath("endpoint")) {
      builder.endpointOverride(URI.create(config.getString("endpoint")))
    }
    if (config.hasPath("region")) {
      builder.region(Region.of(config.getString("region")))
    }

    builder.build()
  }

  @tailrec
  private def getInstances(
      client: Ec2Client,
      filters: List[Filter],
      nextToken: Option[String],
      accumulator: List[String] = Nil): List[String] = {

    val describeInstancesRequest = DescribeInstancesRequest.builder()
      .filters(filters.asJava)
      .nextToken(nextToken.orNull)
      .build()

    val describeInstancesResult = client.describeInstances(describeInstancesRequest)

    val ips: List[String] =
      describeInstancesResult.reservations().asScala.toList
        .flatMap(r => r.instances().asScala.toList)
        .map(instance => instance.privateIpAddress())

    val accumulatedIps = accumulator ++ ips

    Option(describeInstancesResult.nextToken()) match {
      case None =>
        accumulatedIps // aws api has no more results to return, so we return what we have accumulated so far
      case nextPageToken @ Some(_) =>
        // more result items available
        log.debug("aws api returned paginated result, fetching next page!")
        getInstances(client, filters, nextPageToken, accumulatedIps)
    }

  }

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.firstCompletedOf(
      Seq(
        after(resolveTimeout, using = system.scheduler)(
          Future.failed(new TimeoutException(s"Lookup for [$query] timed-out, within [$resolveTimeout]!"))),
        lookup(query)))

  def lookup(query: Lookup): Future[Resolved] = {

    val tagFilter = Filter.builder().name("tag:" + tagKey).values(query.serviceName).build()

    val allFilters: List[Filter] = runningInstancesFilter :: tagFilter :: otherFilters

    Future {
      getInstances(ec2Client, allFilters, None).flatMap((ip: String) =>
        preDefinedPorts match {
          case None =>
            ResolvedTarget(host = ip, port = None, address = Try(InetAddress.getByName(ip)).toOption) :: Nil
          case Some(ports) =>
            ports.map(p =>
              ResolvedTarget(host = ip, port = Some(p),
                address = Try(InetAddress.getByName(ip)).toOption)) // this allows multiple pekko nodes (i.e. JVMs) per EC2 instance
        })
    }.map(resoledTargets => Resolved(query.serviceName, resoledTargets))

  }

}
