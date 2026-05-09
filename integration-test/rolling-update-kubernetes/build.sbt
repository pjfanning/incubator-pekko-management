/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

enablePlugins(JavaAppPackaging, DockerPlugin)

version := "1.3.3.7" // we hard-code the version here, it could be anything really

dockerExposedPorts := Seq(8080, 8558, 2552)
dockerBaseImage := "eclipse-temurin:17-jre-alpine"

dockerCommands ++= Seq(
  Cmd("USER", "root"),
  Cmd("RUN", "/sbin/apk", "add", "--no-cache", "bash", "bind-tools", "busybox-extras", "curl", "strace"),
  Cmd("RUN", "chgrp -R 0 . && chmod -R g=u ."))
