#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [version:'']
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()


    container('clients') {
      def newVersion = ''
      if (config.version == '') {
          newVersion = getNewVersion {}
      } else {
          newVersion = config.version
      }

      def flow = new io.fabric8.Fabric8Commands()
      def utils = new io.fabric8.Utils()
      def namespace = utils.getNamespace()
      echo 'got namespace ' + namespace
      def newImageName = "${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${namespace}/${env.JOB_NAME}:${newVersion}"

      env.setProperty('VERSION',newVersion)

      sh "docker build -t ${newImageName} ."
      if (flow.isSingleNode()){
          sh "echo 'Running on a single node, skipping docker push as not needed'"
      } else {
          sh "docker push ${newImageName}"
      }
      return newVersion
    }

  }
