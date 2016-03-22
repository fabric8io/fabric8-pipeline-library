#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    def fabric8MavenPluginVersion = flow.getReleaseVersion "io/fabric8/fabric8-maven-plugin"

    sh "mvn io.fabric8:fabric8-maven-plugin:${fabric8MavenPluginVersion}:json io.fabric8:fabric8-maven-plugin:${fabric8MavenPluginVersion}:rolling -Dfabric8.environment=${config.environment} -Dfabric8.dockerUser=fabric8/"

  }
