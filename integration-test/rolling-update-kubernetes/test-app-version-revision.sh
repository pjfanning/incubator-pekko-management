#!/bin/bash

set -exu

export NAMESPACE=pekko-rollingupdate-demo-ns
export APP_NAME=pekko-rollingupdate-demo
export PROJECT_NAME=integration-test-rolling-update-kubernetes
export DEPLOYMENT=integration-test/rolling-update-kubernetes/kubernetes/pekko-cluster-app-value-revision.yml

integration-test/scripts/app-version-revision-kubernetes-test.sh
