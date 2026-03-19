/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
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

    "truncate to 63 characters by default (no hash when hashLength is 0)" in {
      val longName = "a" * 100
      AbstractKubernetesLease.makeDNS1039Compatible(longName, 63, 0).length shouldEqual 63
    }

    "truncate to a custom maxLength (no hash when hashLength is 0)" in {
      val longName = "a" * 100
      AbstractKubernetesLease.makeDNS1039Compatible(longName, 40, 0).length shouldEqual 40
    }

    "trim trailing hyphens after truncation (no hash)" in {
      val name = "a" * 30 + "-" + "b" * 30
      val result = AbstractKubernetesLease.makeDNS1039Compatible(name, 31, 0)
      (result should not).endWith("-")
    }

    "not truncate when name fits within maxLength" in {
      val name63 = "a" * 63
      AbstractKubernetesLease.makeDNS1039Compatible(name63, 63, 8) shouldEqual name63
      AbstractKubernetesLease.makeDNS1039Compatible(name63 + "extra", 63, 8) should not equal name63 + "extra"
    }

    "append hash suffix when truncation is needed and hashLength > 0" in {
      val longName = "a" * 100
      val result = AbstractKubernetesLease.makeDNS1039Compatible(longName, 63, 8)
      result.length shouldEqual 63
      result should endWith regex "[a-z2-7]{8}"
      result should include("-")
    }

    "produce a deterministic hash suffix for the same input" in {
      val name = "my-very-long-lease-name-that-exceeds-the-maximum-allowed-kubernetes-length"
      val r1 = AbstractKubernetesLease.makeDNS1039Compatible(name, 63, 8)
      val r2 = AbstractKubernetesLease.makeDNS1039Compatible(name, 63, 8)
      r1 shouldEqual r2
    }

    "produce different hash suffixes for different original names that truncate to the same prefix" in {
      // Both names normalize to 'a' * N, but originate from different strings
      val name1 = "a" * 100
      val name2 = "A" * 100 // normalizes to same 'a'*100 but is a different original
      val r1 = AbstractKubernetesLease.makeDNS1039Compatible(name1, 63, 8)
      val r2 = AbstractKubernetesLease.makeDNS1039Compatible(name2, 63, 8)
      // The prefix is identical but hash suffixes differ because originals differ
      r1 should not equal r2
    }

    "produce different results for long names that differ only in the last character" in {
      // Both names are 70 chars and share the first 69 chars; they truncate to the same prefix
      // without a hash but must produce different DNS1039 results with one
      val base = "a" * 69
      val name1 = base + "b"
      val name2 = base + "c"
      val r1 = AbstractKubernetesLease.makeDNS1039Compatible(name1, 63, 8)
      val r2 = AbstractKubernetesLease.makeDNS1039Compatible(name2, 63, 8)
      r1 should not equal r2
      // Both must be valid length
      r1.length shouldEqual 63
      r2.length shouldEqual 63
    }

    "produce different results for long names that differ only in the last two characters" in {
      val base = "a" * 68
      val name1 = base + "bc"
      val name2 = base + "de"
      val r1 = AbstractKubernetesLease.makeDNS1039Compatible(name1, 63, 8)
      val r2 = AbstractKubernetesLease.makeDNS1039Compatible(name2, 63, 8)
      r1 should not equal r2
      r1.length shouldEqual 63
      r2.length shouldEqual 63
    }

    "produce only valid DNS 1039 characters when hash suffix is added" in {
      val longName = "My-Very-Long-Lease.Name_With_Special-Characters-That-Exceeds-63-Chars-Limit"
      val result = AbstractKubernetesLease.makeDNS1039Compatible(longName, 63, 8)
      result should fullyMatch regex "[a-z][a-z0-9-]*[a-z0-9]"
      result.length should be <= 63
    }

    "respect a custom hashLength" in {
      val longName = "a" * 100
      val result = AbstractKubernetesLease.makeDNS1039Compatible(longName, 63, 12)
      result.length shouldEqual 63
      // last 12 chars are the hash suffix
      result.takeRight(12) should fullyMatch regex "[a-z2-7]{12}"
    }

    "not add hash when hashLength is 0 even if truncation occurs" in {
      val longName = "a" * 100
      val result = AbstractKubernetesLease.makeDNS1039Compatible(longName, 63, 0)
      result shouldEqual "a" * 63
    }

    "hash suffix contains only lowercase letters and digits (no uppercase, no '=' padding)" in {
      // Use many different inputs to exercise the full base32 output, including partial-group chars
      val inputs = Seq(
        "a" * 100,
        "my-very-long-lease-name-that-needs-to-be-truncated-for-kubernetes",
        "UPPER_CASE.DOTTED_NAME-that-is-too-long-for-kubernetes-limit",
        "x" * 70)
      for (name <- inputs) {
        val result = AbstractKubernetesLease.makeDNS1039Compatible(name, 63, 8)
        val suffix = result.takeRight(8)
        withClue(s"suffix '$suffix' of '$result' (from '$name') must match [a-z2-7]{8}: ") {
          suffix should fullyMatch regex "[a-z2-7]{8}"
        }
        withClue(s"result '$result' must not contain '=': ") {
          result should not include "="
        }
      }
    }
  }
}
