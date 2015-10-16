def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def pullRequestId = ""
    def versionBumpPullRequest = ""


    if (config.project == 'ipaas-quickstarts'){

    }
    versionBumpPullRequest = bumpiPaaSQuickstartsVersions{}


    if (versionBumpPullRequest != null){
      waitUntilPullRequestMerged{
        name = config.project
        prId = versionBumpPullRequest
      }
    }

    stagedProjects << stageProject{
      project = config.project
    }

    pullRequestId = releaseFabric8 {
      projectStagingDetails = stagedProjects
      project = config.project
    }

    waitUntilArtifactSyncedWithCentral {
      artifact = config.projectArtifact
    }

    waitUntilPullRequestMerged{
      name = config.project
      prId = quickstartsReleasePR
    }

}
