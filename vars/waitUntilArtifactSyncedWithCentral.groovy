#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    stage "waiting for ${config.artifactId} ${config.version} artifacts to sync with central"

    def flow = new io.fabric8.Fabric8Commands()

    waitUntil {
      flow.isArtifactAvailableInRepo(config.repo, config.groupId.replaceAll('\\.','/'), config.artifactId, config.version, config.ext)
    }

    message =  "${config.artifact} ${config.version} released and available in maven central"
    hubot room: 'release', message: message

}
