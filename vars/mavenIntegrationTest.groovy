#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    sh "mvn org.apache.maven.plugins:maven-failsafe-plugin:2.18.1:integration-test -Dfabric8.environment=${config.environment} -Dit.test=${config.itestPattern} -DfailIfNoTests=${config.failIfNoTests} org.apache.maven.plugins:maven-failsafe-plugin:2.18.1:verify"

  }
