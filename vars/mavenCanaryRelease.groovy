#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    def dockerMavenPluginVersion = flow.getReleaseVersion "io/fabric8/docker-maven-plugin"

    sh "git checkout -b ${env.JOB_NAME}-${config.version}"
    sh "mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -U -DnewVersion=${config.version}"
    sh "mvn clean"

    Model m = readMavenPom file: 'pom.xml'
    def groupId = m.groupId.split( '\\.' )
    def user = groupId[groupId.size()-1].trim()

    sh "mvn install -U org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy io.fabric8:docker-maven-plugin:${dockerMavenPluginVersion}:build -Dfabric8.image=${user}/${env.JOB_NAME}:${config.version} -Dspring.boot.name=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${user}/${env.JOB_NAME}:${config.version} -Dfabric8.dockerUser=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${user}/"

    // until we port the quickstarts to use the new f-m-p we need to force a tag and push to a registry if running in a cluster

    kubernetes.image().withName("${user}/${env.JOB_NAME}:${config.version}").tag().inRepository("${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${user}/${env.JOB_NAME}").withTag("${config.version}")

    if (flow.isSingleNode()){
        echo 'Running on a single node, skipping docker push as not needed'

    } else {

      // couldnt get the docker:push to work, maybe it will work after switching to fabric8:push
      //sh "mvn io.fabric8:docker-maven-plugin:${dockerMavenPluginVersion}:push -Ddocker.push.registry=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT} -Dspring.boot.name=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${user}/${env.JOB_NAME}:${config.version} -Dfabric8.dockerUser=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/fabric8/"
      kubernetes.image().withName("${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${user}/${env.JOB_NAME}").push().withTag("${config.version}").toRegistry()
    }

    if (flow.hasService("content-repository")) {
      try {
        sh 'mvn site site:deploy'
      } catch (err) {
        // lets carry on as maven site isn't critical
        echo 'unable to generate maven site'
      }
    } else {
      echo 'no content-repository service so not deploying the maven site report'
    }
  }
