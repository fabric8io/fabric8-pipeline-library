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
    sh "mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -DnewVersion=${config.version}"
    sh "mvn clean"
    sh "mvn install -U org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy io.fabric8:docker-maven-plugin:${dockerMavenPluginVersion}:build -Dfabric8.dockerUser=fabric8/"
    try {
      sh 'mvn site site:deploy'
    } catch (err) {
      // lets carry on as maven site isn't critical
      echo 'unable to generate maven site'
    }
  }
