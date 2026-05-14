#!/bin/bash

set -exu

export NAMESPACE=pekko-rolling-update-demo-cr-ns
export APP_NAME=pekko-rolling-update-demo
export PROJECT_NAME=integration-test-rolling-update-kubernetes
export CRD=rolling-update-kubernetes/pod-cost.yml
export DEPLOYMENT=integration-test/rolling-update-kubernetes/kubernetes/pekko-cluster-cr.yml

integration-test/scripts/rollingupdate-kubernetes-cr-test.sh
