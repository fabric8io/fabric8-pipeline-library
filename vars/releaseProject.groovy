def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    String versionBumpPullRequest = ""
    def tagDockerImages = []
    def promoteDockerImages = []
    def helm = false

    if (config.project == 'ipaas-quickstarts'){
      versionBumpPullRequest = bumpiPaaSQuickstartsVersions{}

    } else if (config.project == 'fabric8-ipaas'){
      versionBumpPullRequest = bumpFabric8iPaaSVersions{}
      promoteDockerImages = ['amqbroker','api-registry','apiman-gateway','apiman','fabric8mq','fabric8mq-consumer','fabric8mq-producer']
      helm = true

    } else if (config.project == 'fabric8-devops'){
      versionBumpPullRequest = bumpFabric8DevOpsVersions{}
      tagDockerImages = ['hubot-irc','eclipse-orion','nexus','gerrit','fabric8-kiwiirc','brackets','jenkins-jnlp-client','taiga-front','taiga-back','hubot-slack','lets-chat','jenkins-docker']
      promoteDockerImages = ['chaos-monkey','elasticsearch-logstash-template','fabric8-forge','hubot-notifier','image-linker','kibana-config','prometheus-kubernetes','templates']
      helm = true

    } else if (config.project == 'fabric8-console'){
      versionBumpPullRequest = bumpConsoleVersions{}
      tagDockerImages = ['fabric8-console']
      helm = true

    } else if (config.project == 'kubernetes-client'){
      versionBumpPullRequest = bumpKubernetesClientVersions{}

    } else if (config.project == 'fabric8'){
      versionBumpPullRequest = bumpFabric8Versions{}

    }

    if (versionBumpPullRequest != null && versionBumpPullRequest != ""){
      waitUntilPullRequestMerged{
        name = config.project
        prId = versionBumpPullRequest
      }
    }

    def stagedProject = stageProject{
      project = config.project
    }

    // def proceedMessage = "fabric8 released - would you like to continue?"
    // hubotApprove message: proceedMessage, room: "release"
    // input id: 'Proceed', message: "\n${proceedMessage}"

    if (promoteDockerImages.size() > 0){
      promoteImages{
        project = config.project
        images = promoteDockerImages
        tag = stagedProject[1]
      }
    }

    if (tagDockerImages.size() > 0){
      tagImages{
        project = config.project
        images = tagDockerImages
        tag = stagedProject[1]
      }
    }

    String pullRequestId = release {
      projectStagingDetails = stagedProject
      project = config.project
      helmPush = helm
    }

    parallel(central: {
      waitUntilArtifactSyncedWithCentral {
        artifact = config.projectArtifact
        version = stagedProject[1]
      }
    }, prmerged: {
      if (pullRequestId != null){
        waitUntilPullRequestMerged{
          name = config.project
          prId = pullRequestId
        }
      }
    })
}
