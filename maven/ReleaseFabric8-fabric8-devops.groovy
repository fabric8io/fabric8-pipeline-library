def stagedProjects = []

hubot room: 'release', message: "starting fabric8-devops release"
try {

  String devopsPr = bumpFabric8DevOpsVersions{}
  if (devopsPr != null){
    waitUntilPullRequestMerged{
      name = 'fabric8-devops'
      prId = devopsPr
    }
  }

  stagedProjects << stageProject{
    project = 'fabric8-devops'
  }

  String fabric8DevopsPR = releaseFabric8 {
    projectStagingDetails = stagedProjects
    project = 'fabric8-devops'
  }

  waitUntilArtifactSyncedWithCentral {
    artifact = 'devops/distro/distro'
  }
  waitUntilPullRequestMerged{
    name = 'fabric8-devops'
    prId = fabric8DevopsPR
  }
  tagDockerImage{
    project = 'fabric8-devops'
  }

  hubot room: 'release', message: "fabric8-devops released"
} catch (err){
    hubot room: 'release', message: "fabric8-devops release failed ${err}"
    currentBuild.result = 'FAILURE'
}
