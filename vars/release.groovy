#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    stage "release ${config.project}"

      def name = config.projectStagingDetails[0]
      def version = config.projectStagingDetails[1]
      def repoIds = config.projectStagingDetails[2]

      def flow = new io.fabric8.Fabric8Commands()
      unstash name:"staged-${config.project}-${version}"

      echo "About to release ${name} repo ids ${repoIds}"
      for(int j = 0; j < repoIds.size(); j++){
        flow.releaseSonartypeRepo(repoIds[j])
      }

      flow.updateNextDevelopmentVersion(version)

      String pullRequestId = flow.createPullRequest("[CD] Release ${version}","${config.project}")
      echo "pull request id ${pullRequestId}"

      if (config.helmPush) {
        flow.helm()
      }

      return pullRequestId

  }
