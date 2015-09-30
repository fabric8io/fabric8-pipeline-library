def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    // loop over each staged project and delete release branch and nexus staged repo
    for(int i = 0; i < config.projects.size(); i++){
      node {
        ws (config.projects[i].name){
          withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
            flow.dropStagingRepo(config.projects[i].repoId)
            flow.deleteBranch("release-v${config.projects[i].version}")
          }
        }
      }
    }
  }
