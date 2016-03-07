#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    stage "waiting for ${config.artifact} ${config.version} artifacts to sync with central"

    def flow = new io.fabric8.Fabric8Commands()
    def newVersion = config.version

    waitUntil {
      flow.getMavenCentralVersion(config.artifact) == newVersion
    }

    message =  "${config.artifact} ${config.version} released and available in maven central"
    hubot room: 'release', message: message

}
