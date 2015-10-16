def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    stage "release ${config.project}"
    node ('swarm'){
      ws (config.project){
        withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
        for(int i = 0; i < config.projectStagingDetails.size(); i++){
          def name = config.projectStagingDetails[i][0]
          def version = config.projectStagingDetails[i][1]
          def repoIds = config.projectStagingDetails[i][2]
          // get the staging details for the project we are working on
          if (name == config.project){

            def flow = new io.fabric8.Fabric8Commands()
            flow.setupWorkspace(name)
            sh "git fetch"
            sh "git checkout release-v${version}"

            // push any docker images before we release sonartype repos
            if (name == 'fabric8-devops' || name == 'fabric8-ipaas'){
              flow.dockerPush()
            }

            echo "About to release ${name} repo ids ${repoIds}"
            for(int j = 0; j < repoIds.size(); j++){
              flow.releaseSonartypeRepo(repoIds[j])
            }

            flow.updateNextDevelopmentVersion(version)

            String pullRequestId = flow.createPullRequest("[CD] Release ${version}")
            echo "pull request id ${pullRequestId}"
            return pullRequestId
          }
        }
       }
      }
    }
  }
