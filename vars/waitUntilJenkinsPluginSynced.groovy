#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()

    def repo = config.repo
    if (!repo) {
        repo = "http://archives.jenkins-ci.org/"
    }
    def name = config.name
    def path = "plugins/" + name
    def artifact = "${name}.hpi"
    
    waitUntil {
        retry(3){
            flow.isFileAvailableInRepo(repo, path, config.version, artifact)
        }
    }

    message = "${config.artifactId} ${config.version} released and available in the jenkins plugin archive"
    hubotSend message: message, failOnError: false

}
