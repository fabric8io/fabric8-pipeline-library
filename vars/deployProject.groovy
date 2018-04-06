#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def projectName = config.stagedProject[0]
    def releaseVersion = config.stagedProject[1]

    //comment out as we're not using helm charts
    //unstash name:"staged-${projectName}-${releaseVersion}".hashCode().toString()

    def rc = readFile config.resourceLocation

    kubernetesApply(file: rc, environment: config.environment, registry: "${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}")

}
