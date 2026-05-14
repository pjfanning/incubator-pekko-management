#!/bin/bash

set -exu

export NAMESPACE=pekko-rolling-update-demo-ns
export APP_NAME=pekko-rolling-update-demo
export PROJECT_NAME=integration-test-rolling-update-kubernetes
export DEPLOYMENT=integration-test/rolling-update-kubernetes/kubernetes/pekko-cluster.yml

integration-test/scripts/rollingupdate-kubernetes-test.sh
