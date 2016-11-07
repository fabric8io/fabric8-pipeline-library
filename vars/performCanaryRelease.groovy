#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [version:'']
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def newVersion = ''
    if (config.version == '') {
        newVersion = getNewVersion {}
    } else {
        newVersion = config.version
    }

    def flow = new io.fabric8.Fabric8Commands()
    def utils = new io.fabric8.Utils()
    def namespace = utils.getNamespace()
    def newImageName = "${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${namespace}/${env.JOB_NAME}"

    env.setProperty('VERSION',newVersion)

    kubernetes.image().withName("${env.JOB_NAME}").build().fromPath(".")
    kubernetes.image().withName("${env.JOB_NAME}").tag().inRepository(newImageName).withTag(newVersion)

    if (flow.isSingleNode()){
        echo 'Running on a single node, skipping docker push as not needed'
    } else {
        kubernetes.image().withName(newImageName).push().withTag(newVersion).toRegistry()
    }

    return newVersion
  }
