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

package org.apache.pekko.rollingupdate.kubernetes

import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.http.scaladsl.model.HttpMethods.PATCH
import org.apache.pekko.http.scaladsl.model.headers.Authorization
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.MediaTypes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.util.ByteString

import scala.collection.immutable

/**
 * INTERNAL API
 */
@InternalApi private[kubernetes] object ApiRequests {

  def podDeletionCost(settings: KubernetesSettings, apiToken: String, namespace: String, cost: Int): HttpRequest = {
    val path = Uri.Path.Empty / "api" / "v1" / "namespaces" / namespace / "pods" / settings.podName
    val scheme = if (settings.secure) "https" else "http"
    val uri = Uri.from(scheme, host = settings.apiServiceHost, port = settings.apiServicePort).withPath(path)
    val headers = if (settings.secure) immutable.Seq(Authorization(OAuth2BearerToken(apiToken))) else Nil

    HttpRequest(
      method = PATCH,
      uri = uri,
      headers = headers,
      entity = HttpEntity(
        MediaTypes.`application/merge-patch+json`,
        ByteString(
          s"""{"metadata": {"annotations": {"controller.kubernetes.io/pod-deletion-cost": "$cost" }}}"""
        ))
    )
  }

}
