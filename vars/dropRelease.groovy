#!/usr/bin/groovy
stage 'drop dryrun release'
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def flow = new io.fabric8.Fabric8Commands()

    // loop over each staged project and delete release branch and nexus staged repo
    for(int i = 0; i < config.projects.size(); i++){

      name = config.projects[i][0]
      version = config.projects[i][1]
      repoIds = config.projects[i][2]


      withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
        for(int j = 0; j < repoIds.size(); j++){
          echo "About to drop release repo id ${repoIds[j]}"
          flow.dropStagingRepo(repoIds[j])
        }
        flow.deleteRemoteBranch("release-v${version}")
      }      
    }
  }
