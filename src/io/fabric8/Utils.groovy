#!/usr/bin/groovy
package io.fabric8

import com.cloudbees.groovy.cps.NonCPS
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient

@NonCPS
def environmentNamespace(environment) {
  KubernetesClient kubernetes = new DefaultKubernetesClient();
  return kubernetes.getNamespace() + "-${environment}"
}

@NonCPS
def getNamespace() {
  KubernetesClient kubernetes = new DefaultKubernetesClient();
  return kubernetes.getNamespace()
}

return this;
