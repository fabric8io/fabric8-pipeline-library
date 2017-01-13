#!/usr/bin/groovy
import io.fabric8.Fabric8Commands

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [version: '']
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    container('clients') {
        def newVersion = config.version
        if (newVersion == '') {
            newVersion = getNewVersion {}
        }

        env.setProperty('VERSION', newVersion)

        def flow = new Fabric8Commands()
        if (flow.isOpenShift()) {
            s2iBuild(newVersion)
        } else {
            dockerBuild(newVersion)
        }

        return newVersion
    }
}

def dockerBuild(version){
    def utils = new io.fabric8.Utils()
    def namespace = utils.getNamespace()
    def newImageName = "${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${namespace}/${env.JOB_NAME}:${version}"

    sh "docker build -t ${newImageName} ."
    if (flow.isSingleNode()) {
        sh "echo 'Running on a single node, skipping docker push as not needed'"
    } else {
        sh "docker push ${newImageName}"
    }
}

def s2iBuild(version){

    def is = getImageStream()
    def bc = getBuildConfig(version)

    kubernetesApply(file: is, environment: 'default')
    kubernetesApply(file: bc, environment: 'default')
    sh "oc start-build ${env.JOB_NAME}-s2i --from-dir ../${env.JOB_NAME} --follow"

}

def getImageStream(){
    return """
apiVersion: v1
kind: ImageStream
metadata:
  name: ${env.JOB_NAME}
"""
}

def getBuildConfig(version){
    return """
apiVersion: v1
kind: BuildConfig
metadata:
  name: ${env.JOB_NAME}-s2i
spec:
  output:
    to:
      kind: ImageStreamTag
      name: ${env.JOB_NAME}:${version}
  runPolicy: Serial
  source:
    type: Binary
  strategy:
    type: Docker
"""
}