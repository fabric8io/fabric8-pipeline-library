def stagedProjects = []

hubot room: 'release', message: "starting fabric8-devops release"
try {

  stage 'bump fabric8-devops release dependency versions'
  String devopsPr = bumpFabric8DevOpsVersions{}
  if (devopsPr != null){
    waitUntilPullRequestMerged{
      name = 'fabric8-devops'
      prId = devopsPr
    }
  }

  stage 'stage fabric8-devops'
  stagedProjects << stageProject{
    project = 'fabric8-devops'
  }

  stage 'release fabric8-devops'
  String fabric8DevopsPR = releaseFabric8 {
    projectStagingDetails = stagedProjects
    project = 'fabric8-devops'
  }

  stage 'wait for artifacts to sync with central'
  waitUntilArtifactSyncedWithCentral {
    artifact = 'devops/distro/distro'
  }

  stage 'wait for GitHub pull request merge'
  waitUntilPullRequestMerged{
    name = 'fabric8-devops'
    prId = fabric8DevopsPR
  }

  stage 'tag fabric8-devops docker images'
  tagDockerImage{
    images = ['hubot-irc','eclipse-orion','nexus','gerrit','fabric8-kiwiirc','brackets','jenkins-swarm-client','taiga-front','taiga-back','hubot-slack','lets-chat','jenkernetes']
  }

  hubot room: 'release', message: "fabric8-devops released"
} catch (err){
    hubot room: 'release', message: "fabric8-devops release failed ${err}"
    currentBuild.result = 'FAILURE'
}
