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

package jdoc.pekko.rollingupdate.kubernetes;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.rollingupdate.kubernetes.PodDeletionCost;

public class PodDeletionCostCompileOnly {
    public static void bootstrap() {

        ActorSystem system = ActorSystem.create();

        //#start
        // Starting the pod deletion cost annotator
        PodDeletionCost.get(system).start();
        //#start
    }
}
