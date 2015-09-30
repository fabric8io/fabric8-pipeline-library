def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    node {
      ws (config.project){
        withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

        for(int i = 0; i < config.projects.size(); i++){
          def projectStagingDetails = config.projects[i]

          // get the staging details for the project we are working on
          if (projectStagingDetails.name == config.project){

            def flow = new io.fabric8.Release()

            flow.setupWorkspace(projectStagingDetails.name)

            def releaseVersion = projectStagingDetails.version
            sh "git fetch"
            sh "git checkout release-v${releaseVersion}"

            if (projectStagingDetails.name == 'fabric8-devops' || projectStagingDetails.name == 'fabric8-ipaas' || projectStagingDetails.name == 'ipaas-quickstarts'){
              flow.dockerPush()
            }

            flow.releaseSonartypeRepo(projectStagingDetails.repoId)

            return flow.createPullRequest("[CD] Release ${releaseVersion}")
          }
        }
      }
    }
  }
