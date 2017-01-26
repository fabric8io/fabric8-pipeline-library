#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    // until we fix up pushing tags to remote repos with correct secrets lets default to use the short commit sha as the version
    echo 'NOTE: until we support pushing tags to remote repos with correct secrets lets default to use the short commit sha as the version'

    def version = sh(script: 'git rev-parse --short HEAD', returnStdout: true).toString().trim()
    version = 'v' + version
    echo 'using new version ' + version
    return version
}
