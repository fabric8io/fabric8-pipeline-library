#!/usr/bin/groovy
package io.fabric8;

def environmentNamespace(environment){
  return "${env.KUBERNETES_NAMESPACE}-${environment}"
}

return this;
