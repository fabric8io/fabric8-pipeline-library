#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    stage "promote ${config.project} docker images"

    kubernetes.pod('buildpod').withImage('fabric8/builder-openshift-client')
    .withPrivileged(true)
    .withHostPathMount('/var/run/docker.sock','/var/run/docker.sock')
    .withEnvVar('DOCKER_CONFIG','/root/.docker/')
    .withSecret('jenkins-docker-cfg','/root/.docker')
    .inside {

      for(int i = 0; i < config.images.size(); i++){

        image = config.images[i]

        sh "docker pull ${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/fabric8/${image}:${config.tag}"
        sh "docker tag -f ${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${config.org}/${image}:${config.tag} ${config.toRegistry}/${config.org}/${image}:${config.tag}"

        retry (3){
          sh "docker push -f ${config.toRegistry}/${config.org}/${image}:${config.tag}"
        }

            // NOT YET IMPLEMENTED - WE NEED TO SWITCH TO OPENSHIFT BINARY BUILDER SO OC SHOULD PROBABLY DO THE IMAGE TAGGING
            // WE HIT AN ISSUE WITH KUBERNETES-WORKFLOW WHEN TAGGING AN IMAGE NOT BUILT ON THE SAME NODE BUT NO WAY TO PULL THE IMAGE FIRST USING KUBERNETES-WORKFLOW.
            // NOT SURE IF THATS A PROBLEM THOUGH
            // kubernetes.image().withName("${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${config.org}/${image}:${config.tag}").tag().force().inRepository("${config.toRegistry}/${config.org}/${image}").withTag("${config.tag}")
            // retry (3){
            //   kubernetes.image().withName("${config.toRegistry}/${config.org}/${image}").push().force().withTag("${config.tag}").toRegistry()
            // }

      }
    }
  }
