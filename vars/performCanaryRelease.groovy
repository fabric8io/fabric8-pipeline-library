#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
import io.fabric8.Utils

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
    def utils = new Utils()
    def flow = new Fabric8Commands()
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

    def utils = new Utils()
    def ns = utils.namespace
    def resourceName = utils.getResourceName()
    def is = getImageStream(ns, resourceName)
    def bc = getBuildConfig(ns, resourceName, version)

    sh "oc delete is ${resourceName} -n ${ns} || true"
    kubernetesApply(file: is, environment: ns)
    kubernetesApply(file: bc, environment: ns)
    sh "oc start-build ${resourceName}-s2i --from-dir ./ --follow -n ${ns}"

}

def getImageStream(ns, resourceName){
    return """
apiVersion: v1
kind: ImageStream
metadata:
  name: ${resourceName}
  namespace: ${ns}
"""
}

def getBuildConfig(ns, resourceName, version){
    return """
apiVersion: v1
kind: BuildConfig
metadata:
  name: ${resourceName}-s2i
  namespace: ${ns}
spec:
  output:
    to:
      kind: ImageStreamTag
      name: ${resourceName}:${version}
  runPolicy: Serial
  source:
    type: Binary
  strategy:
    type: Docker
"""
}
