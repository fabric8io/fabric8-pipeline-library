#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    def version
    container(name: 'maven') {

        // update any versions that we want to override
        for (v in config.pomVersionToUpdate) {
            flow.searchAndReplaceMavenVersionProperty(v.key, v.value)
        }

        sh "mvn clean -e -U install -Ddocker.push.registry=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}"

        def m = readMavenPom file: 'pom.xml'
        version = m.version

    }

    container(name: 'docker'){
        if (config.extraImagesToStage != null){
            stageExtraImages {
                images = config.extraImagesToStage
                tag = version
            }
        }
    }

    return version
}
