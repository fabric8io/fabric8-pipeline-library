#!/usr/bin/groovy
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()


    def newVersion = getNewVersion{}

    env.setProperty('VERSION',newVersion)

    kubernetes.image().withName("${env.JOB_NAME}").build().fromPath(".")
    kubernetes.image().withName("${env.JOB_NAME}").tag().inRepository("${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${env.KUBERNETES_NAMESPACE}/${env.JOB_NAME}").withTag(newVersion)

    if (isSingleNode()){
        echo 'Running on a single node, skipping docker push as not needed'
    } else {
        kubernetes.image().withName("${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${env.KUBERNETES_NAMESPACE}/${env.JOB_NAME}").push().withTag(newVersion).toRegistry()
    }

    return newVersion
  }

  @NonCPS
  def isSingleNode() {
    KubernetesClient kubernetes = new DefaultKubernetesClient();
    if (kubernetes.nodes().list().getItems().size() == 1){
      return true
    } else {
      return false
    }
  }
