/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.rollingupdate.kubernetes

import scala.concurrent.duration._
import org.apache.pekko.util.JavaDurationConverters._
import org.apache.pekko.annotation.InternalApi
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi
private[kubernetes] object KubernetesSettings {

  private implicit class HasDefined(val config: Config) {
    def hasDefined(key: String): Boolean =
      config.hasPath(key) &&
      config.getString(key).trim.nonEmpty &&
      config.getString(key) != s"<$key>"

    def optDefinedValue(key: String): Option[String] =
      if (hasDefined(key)) Some(config.getString(key)) else None
  }

  def apply(config: Config): KubernetesSettings = {
    val crName = config.getString("custom-resource.cr-name") match {
      case ""   => None
      case name => Some(name)
    }

    val customResourceSettings = new CustomResourceSettings(
      enabled = config.getBoolean("custom-resource.enabled"),
      crName = crName,
      cleanupAfter = config.getDuration("custom-resource.cleanup-after").asScala
    )

    new KubernetesSettings(
      config.getString("api-ca-path"),
      config.getString("api-token-path"),
      config.getString("api-service-host"),
      config.getInt("api-service-port"),
      config.optDefinedValue("namespace"),
      config.getString("namespace-path"),
      config.getString("pod-name"),
      config.getBoolean("secure-api-server"),
      config.getDuration("api-service-request-timeout").asScala,
      customResourceSettings
    )
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[kubernetes] class KubernetesSettings(
    val apiCaPath: String,
    val apiTokenPath: String,
    val apiServiceHost: String,
    val apiServicePort: Int,
    val namespace: Option[String],
    val namespacePath: String,
    val podName: String,
    val secure: Boolean,
    val apiServiceRequestTimeout: FiniteDuration,
    val customResourceSettings: CustomResourceSettings,
    val bodyReadTimeout: FiniteDuration = 1.second
)

/**
 * INTERNAL API
 */
@InternalApi
private[kubernetes] class CustomResourceSettings(
    val enabled: Boolean,
    val crName: Option[String],
    val cleanupAfter: FiniteDuration
)
