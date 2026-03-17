/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.coordination.lease.kubernetes

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MakeDNS1039CompatibleSpec extends AnyWordSpec with Matchers {

  "makeDNS1039Compatible" should {

    "leave a simple lowercase name unchanged" in {
      AbstractKubernetesLease.makeDNS1039Compatible("my-lease") shouldEqual "my-lease"
    }

    "convert underscores and dots to hyphens" in {
      AbstractKubernetesLease.makeDNS1039Compatible("my.lease_name") shouldEqual "my-lease-name"
    }

    "strip leading and trailing hyphens after normalization" in {
      AbstractKubernetesLease.makeDNS1039Compatible("-my-lease-") shouldEqual "my-lease"
    }

    "remove characters that are not allowed in DNS 1039 labels" in {
      AbstractKubernetesLease.makeDNS1039Compatible("my@lease!name") shouldEqual "myleasename"
    }

    "convert uppercase to lowercase" in {
      AbstractKubernetesLease.makeDNS1039Compatible("MyLease") shouldEqual "mylease"
    }

    "truncate to 63 characters by default" in {
      val longName = "a" * 100
      AbstractKubernetesLease.makeDNS1039Compatible(longName).length shouldEqual 63
    }

    "truncate to a custom maxLength" in {
      val longName = "a" * 100
      AbstractKubernetesLease.makeDNS1039Compatible(longName, 40).length shouldEqual 40
    }

    "trim trailing hyphens after truncation" in {
      // name that after truncation ends with a hyphen should have it removed
      val name = "a" * 30 + "-" + "b" * 30
      val result = AbstractKubernetesLease.makeDNS1039Compatible(name, 31)
      (result should not).endWith("-")
    }

    "default maxLength of 63 is unchanged" in {
      val name63 = "a" * 63
      AbstractKubernetesLease.makeDNS1039Compatible(name63) shouldEqual name63
      AbstractKubernetesLease.makeDNS1039Compatible(name63 + "extra") shouldEqual name63
    }
  }
}
