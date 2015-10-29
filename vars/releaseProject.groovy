def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    String versionBumpPullRequest = ""
    def tagDockerImages = []
    def promoteDockerImages = []

    if (config.project == 'ipaas-quickstarts'){
      versionBumpPullRequest = bumpiPaaSQuickstartsVersions{}

    } else if (config.project == 'fabric8-ipaas'){
      versionBumpPullRequest = bumpFabric8iPaaSVersions{}
      promoteDockerImages = ['amqbroker','api-registry','apiman-gateway','apiman','fabric8mq','fabric8mq-consumer','fabric8mq-producer']

    } else if (config.project == 'fabric8-devops'){
      versionBumpPullRequest = bumpFabric8DevOpsVersions{}
      tagDockerImages = ['fabric8-console','hubot-irc','eclipse-orion','nexus','gerrit','fabric8-kiwiirc','brackets','jenkins-swarm-client','taiga-front','taiga-back','hubot-slack','lets-chat','jenkernetes']
      promoteDockerImages = ['chaos-monkey','elasticsearch-logstash-template','fabric8-forge','hubot-notifier','image-linker','kibana-config','prometheus-kubernetes','templates']

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

    if (promoteDockerImages.size() > 0){
      promoteImages{
        project = config.project
        images = promoteDockerImages
        tag = stagedProject[1]
      }
    }

    parallel(central: {
      waitUntilArtifactSyncedWithCentral {
        artifact = config.projectArtifact
        version = stagedProject[1]
      }
    }, tag: {
      if (tagDockerImages.size() > 0){
        tagImages{
          project = config.project
          images = tagDockerImages
          tag = stagedProject[1]
        }
      }
    })

    String pullRequestId = release {
      projectStagingDetails = stagedProject
      project = config.project
    }

    if (pullRequestId != null){
      waitUntilPullRequestMerged{
        name = config.project
        prId = pullRequestId
      }
    }
}
