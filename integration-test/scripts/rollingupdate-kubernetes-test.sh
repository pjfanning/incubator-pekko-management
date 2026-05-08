#!/bin/bash

set -exu

NAMESPACE=pekko-rollingupdate-demo-ns
DEPLOYMENT=pekko-rollingupdate-demo

setup() {
  kubectl create namespace ${NAMESPACE} || true
  kubectl apply -f integration-test/rollingupdate-kubernetes/kubernetes/pekko-cluster.yml
  kubectl rollout status deployment/${DEPLOYMENT} -n ${NAMESPACE} --timeout=3m
}

test() {
  # Scale cluster to 4 pods
  kubectl scale deployment ${DEPLOYMENT} --replicas=4 -n ${NAMESPACE}
  kubectl rollout status deployment/${DEPLOYMENT} -n ${NAMESPACE} --timeout=3m

  # Wait for annotations to be set
  sleep 30

  # Check pod-deletion-cost annotations
  echo "Pods:"
  PODS=$(kubectl get pods -n ${NAMESPACE} -o jsonpath='{.items[*].metadata.name}')
  echo "$PODS"
  if [ -z "$PODS" ]; then
    echo "No pods found!"
    exit 1
  fi

  for pod in $PODS; do
    ANNOTATION=$(kubectl get pod ${pod} -n ${NAMESPACE} -o jsonpath='{.metadata.annotations.controller\.kubernetes\.io/pod-deletion-cost}' || echo "")
    echo "Pod ${pod} has deletion-cost annotation: ${ANNOTATION}"
  done
}

teardown() {
  kubectl delete -f integration-test/rollingupdate-kubernetes/kubernetes/pekko-cluster.yml || true
  kubectl delete namespace ${NAMESPACE} || true
}

if test -n "${1:-}"; then
  $1
else
  setup
  test
  teardown
fi
