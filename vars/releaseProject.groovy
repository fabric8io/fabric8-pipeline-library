def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def versionBumpPullRequest = ""

    if (config.project == 'ipaas-quickstarts'){
      versionBumpPullRequest = bumpiPaaSQuickstartsVersions{}
    } else if (config.project == 'fabric8-ipaas'){
      versionBumpPullRequest = bumpFabric8iPaaSVersions{}
    } else if (config.project == 'fabric8-devops'){
      versionBumpPullRequest = bumpFabric8DevOpsVersions{}
    } else if (config.project == 'kubernetes-client'){
      versionBumpPullRequest = bumpKubernetesClientVersions{}
    } else if (config.project == 'fabric8'){
      versionBumpPullRequest = bumpFabric8Versions{}
    }

    if (versionBumpPullRequest != null){
      waitUntilPullRequestMerged{
        name = config.project
        prId = versionBumpPullRequest
      }
    }

    def stagedProjects = stageProject{
      project = config.project
    }

    def pullRequestId = release {
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
