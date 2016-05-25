#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    def repoIds = config.stagedProject[2]

    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
      for(int j = 0; j < repoIds.size(); j++){
        echo "About to drop release repo id ${repoIds[j]}"
        flow.dropStagingRepo(repoIds[j])
      }
    }
    flow.drop(config.pullRequestId, config.stagedProject[0])
  }
