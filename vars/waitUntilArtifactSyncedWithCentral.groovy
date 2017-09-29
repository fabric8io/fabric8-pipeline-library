#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()

    waitUntil {
        retry(3){
            flow.isArtifactAvailableInRepo(config.repo, config.groupId.replaceAll('\\.', '/'), config.artifactId, config.version, config.ext)
        }
    }

    message = "${config.artifactId} ${config.version} released and available in maven central"
    hubotSend message: message, failOnError: false

}
