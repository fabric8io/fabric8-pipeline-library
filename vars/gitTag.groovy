#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    flow.setupGitSSH()
    def skipVersionPrefix = config.skipVersionPrefix ?: false

    if (skipVersionPrefix) {
        sh "git tag -fa ${config.releaseVersion} -m 'Release version ${config.releaseVersion}'"
        sh "git push origin ${config.releaseVersion}"
    } else {
        sh "git tag -fa v${config.releaseVersion} -m 'Release version ${config.releaseVersion}'"
        sh "git push origin v${config.releaseVersion}"
    }
}
