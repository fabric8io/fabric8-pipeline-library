#!/usr/bin/groovy
import com.cloudbees.groovy.cps.NonCPS
import io.fabric8.Fabric8Commands

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def version
    container(name: 'maven') {

        // update any versions that we want to override
        overwriteDeps(config.pomVersionToUpdate)

        sh "mvn clean -e -U install -Duser.home=/root/ -Ddocker.push.registry=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}"

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

@NonCPS
def overwriteDeps(versions){
    def flow = new Fabric8Commands()
    for (v in versions) {
        flow.searchAndReplaceMavenVersionPropertyNoCommit(v.key, v.value)
    }
}

