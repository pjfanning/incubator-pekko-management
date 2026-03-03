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

package org.apache.pekko.discovery.awsapi.ec2;

// #custom-client-config
// package com.example;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;

/**
 * Example showing how to build a custom Ec2Client with AWS SDK v2.
 * For most use-cases, region and endpoint are configured via
 * pekko.discovery.aws-api-ec2-tag-based.region and
 * pekko.discovery.aws-api-ec2-tag-based.endpoint in application.conf.
 */
class MyConfiguration {

  public Ec2Client buildClient() {
    return Ec2Client.builder()
        .region(Region.US_EAST_1)
        .build();
  }
}
// #custom-client-config
