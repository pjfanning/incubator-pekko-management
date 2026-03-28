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

package org.apache.pekko.rollingupdate

import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.cluster.Member

import scala.collection.SortedSet

/**
 *  INTERNAL API
 *  Defines a trait for calculating the cost of removing a member from the pekko cluster,
 *  given said member and the list of the members of the cluster from oldest to newest.
 */
@InternalApi private[rollingupdate] trait CostStrategy {
  def costOf(member: Member, membersByAgeDesc: SortedSet[Member]): Option[Int]
}

/**
 * INTERNAL API
 */
@InternalApi private[rollingupdate] object OlderCostsMore extends CostStrategy {
  def costOf(member: Member, membersByAgeDesc: SortedSet[Member]): Option[Int] = {
    val maxCost = 10000
    // avoiding using subsequent numbers: gives room for evolution and allows for manual interventions
    val stepCost = 100

    membersByAgeDesc.zipWithIndex.collectFirst {
      case (m, cost) if m.uniqueAddress == member.uniqueAddress => maxCost - (cost * stepCost)
    }
  }
}
