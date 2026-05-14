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

package doc.pekko.rollingupdate.kubernetes

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.rollingupdate.kubernetes.AppVersionRevision

object AppVersionRevisionCompileOnly {

  val system = ActorSystem()

  // #start
  // Starting the AppVersionRevision extension
  // preferred to be called before ClusterBootstrap
  AppVersionRevision(system).start()
  // #start

}
