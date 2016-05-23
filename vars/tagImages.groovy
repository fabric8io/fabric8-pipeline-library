#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    //stage "tag images"
    kubernetes.pod('buildpod').withImage('fabric8/builder-openshift-client')
    .withPrivileged(true)
    .withHostPathMount('/var/run/docker.sock','/var/run/docker.sock')
    .withEnvVar('DOCKER_CONFIG','/root/.docker/')
    .withSecret('jenkins-docker-cfg','/root/.docker')
    .inside {
      for(int i = 0; i < config.images.size(); i++){
        image = config.images[i]
        retry (3){
          sh "docker pull ${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/fabric8/${image}:${config.tag}"
          sh "docker tag -f ${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/fabric8/${image}:${config.tag} docker.io/fabric8/${image}:${config.tag}"
          sh "docker push -f docker.io/fabric8/${image}:${config.tag}"
        }
      }
    }
  }
