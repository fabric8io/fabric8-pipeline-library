def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    String versionBumpPullRequest = ""
    def tagDockerImages = []

    if (config.project == 'ipaas-quickstarts'){
      versionBumpPullRequest = bumpiPaaSQuickstartsVersions{}
    } else if (config.project == 'fabric8-ipaas'){
      versionBumpPullRequest = bumpFabric8iPaaSVersions{}
    } else if (config.project == 'fabric8-devops'){
      versionBumpPullRequest = bumpFabric8DevOpsVersions{}
      tagDockerImages = ['fabric8-console','hubot-irc','eclipse-orion','nexus','gerrit','fabric8-kiwiirc','brackets','jenkins-swarm-client','taiga-front','taiga-back','hubot-slack','lets-chat','jenkernetes']
    } else if (config.project == 'kubernetes-client'){
      versionBumpPullRequest = bumpKubernetesClientVersions{}
    } else if (config.project == 'fabric8'){
      versionBumpPullRequest = bumpFabric8Versions{}
    }

    if (versionBumpPullRequest != ""){
      waitUntilPullRequestMerged{
        name = config.project
        prId = versionBumpPullRequest
      }
    }

    def stagedProject = stageProject{
      project = config.project
    }

    String pullRequestId = release {
      projectStagingDetails = stagedProject
      project = config.project
    }

    parallel(central: {
      waitUntilArtifactSyncedWithCentral {
        artifact = config.projectArtifact
        version = stagedProject[1]
      }
    }, tag: {
      if (tagDockerImages.size() > 0){
        tagDockerImage{
          project = config.project
          images = tagDockerImages
          tag = stagedProject[1]
        }
      }
    })

    if (pullRequestId != null){
      waitUntilPullRequestMerged{
        name = config.project
        prId = pullRequestId
      }
    }
}
