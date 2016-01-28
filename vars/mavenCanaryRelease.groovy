#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    def dockerMavenPluginVersion = flow.getReleaseVersion "org/jolokia/docker-maven-plugin"

    stage 'canary release'

    sh "git checkout -b ${env.JOB_NAME}-${config.version}"
    sh "mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -DnewVersion=${config.version}"
    sh "mvn clean install -U org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy org.jolokia:docker-maven-plugin:${dockerMavenPluginVersion}:build -Dfabric8.dockerUser=fabric8/ -Ddocker.registry=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}"

  }
