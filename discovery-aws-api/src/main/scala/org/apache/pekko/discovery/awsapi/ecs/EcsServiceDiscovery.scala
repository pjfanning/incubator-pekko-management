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

package org.apache.pekko.discovery.awsapi.ecs

import java.net.{ InetAddress, NetworkInterface, URI }
import java.util.concurrent.TimeoutException

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.discovery.{ Lookup, ServiceDiscovery }
import pekko.discovery.ServiceDiscovery.{ Resolved, ResolvedTarget }
import pekko.discovery.awsapi.ecs.EcsServiceDiscovery.resolveTasks
import pekko.pattern.after

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.retries.DefaultRetryStrategy
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.{ DescribeTasksRequest, DesiredStatus, ListTasksRequest, Task }

import scala.annotation.tailrec
import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Try

final class EcsServiceDiscovery(system: ActorSystem) extends ServiceDiscovery {

  private[this] val config = system.settings.config.getConfig("pekko.discovery.aws-api-ecs")
  private[this] val cluster = config.getString("cluster")

  private[this] lazy val ecsClient = {
    // we have our own retry/backoff mechanism, so we don't need ECS client's in addition
    val overrideConfig =
      ClientOverrideConfiguration.builder().retryStrategy(DefaultRetryStrategy.doNotRetry()).build()
    val builder = EcsClient.builder().overrideConfiguration(overrideConfig)

    if (config.hasPath("endpoint")) {
      builder.endpointOverride(URI.create(config.getString("endpoint")))
    }
    if (config.hasPath("region")) {
      builder.region(Region.of(config.getString("region")))
    }

    builder.build()
  }

  private[this] implicit val ec: ExecutionContext = system.dispatcher

  override def lookup(query: Lookup, resolveTimeout: FiniteDuration): Future[Resolved] =
    Future.firstCompletedOf(
      Seq(
        after(resolveTimeout, using = system.scheduler)(
          Future.failed(new TimeoutException("Future timed out!"))),
        Future {
          Resolved(
            serviceName = query.serviceName,
            addresses = for {
              task <- resolveTasks(ecsClient, cluster, query.serviceName)
              container <- task.containers().asScala
              networkInterface <- container.networkInterfaces().asScala
            } yield {
              val address = networkInterface.privateIpv4Address()
              ResolvedTarget(host = address, port = None, address = Try(InetAddress.getByName(address)).toOption)
            })
        }))

}

object EcsServiceDiscovery {

  // InetAddress.getLocalHost.getHostAddress throws an exception when running
  // in awsvpc mode because the container name cannot be resolved.
  // ECS provides a metadata file
  // (https://docs.aws.amazon.com/AmazonECS/latest/developerguide/container-metadata.html)
  // that we ought to be able to use instead to find our IP address, but the
  // metadata file does not get set when running on Fargate. So this is our
  // only option for determining what the canonical Apache Pekko and pekko-management
  // hostname values should be set to.
  private[awsapi] def getContainerAddress: Either[String, InetAddress] =
    NetworkInterface.getNetworkInterfaces.asScala
      .flatMap(_.getInetAddresses.asScala)
      .filterNot(_.isLoopbackAddress)
      .filter(_.isSiteLocalAddress)
      .toList match {
      case List(value) =>
        Right(value)

      case other =>
        Left(s"Exactly one private address must be configured (found: $other).")
    }

  private def resolveTasks(ecsClient: EcsClient, cluster: String, serviceName: String): Seq[Task] = {
    val taskArns = listTaskArns(ecsClient, cluster, serviceName)
    val tasks = describeTasks(ecsClient, cluster, taskArns)
    tasks
  }

  @tailrec private[this] def listTaskArns(
      ecsClient: EcsClient,
      cluster: String,
      serviceName: String,
      pageTaken: Option[String] = None,
      accumulator: Seq[String] = Seq.empty): Seq[String] = {
    val listTasksResult = ecsClient.listTasks(
      ListTasksRequest.builder()
        .cluster(cluster)
        .serviceName(serviceName)
        .nextToken(pageTaken.orNull)
        .desiredStatus(DesiredStatus.RUNNING)
        .build())
    val accumulatedTasksArns = accumulator ++ listTasksResult.taskArns().asScala
    listTasksResult.nextToken() match {
      case null =>
        accumulatedTasksArns

      case nextPageToken =>
        listTaskArns(
          ecsClient,
          cluster,
          serviceName,
          Some(nextPageToken),
          accumulatedTasksArns)
    }
  }

  private[this] def describeTasks(ecsClient: EcsClient, cluster: String, taskArns: Seq[String]): Seq[Task] =
    for {
      // Each DescribeTasksRequest can contain at most 100 task ARNs.
      group <- taskArns.grouped(100).toList
      tasks = ecsClient.describeTasks(
        DescribeTasksRequest.builder().cluster(cluster).tasks(group.asJava).build())
      task <- tasks.tasks().asScala
    } yield task

}
