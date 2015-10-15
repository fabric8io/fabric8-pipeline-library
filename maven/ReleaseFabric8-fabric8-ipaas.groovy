def stagedProjects = []

hubot room: 'release', message: "starting fabric8-ipaas release"
try {
  String ipaasPr = bumpFabric8iPaaSVersions{}
  if (ipaasPr != null){
    waitUntilPullRequestMerged{
      name = 'fabric8-ipaas'
      prId = ipaasPr
    }
  }

  stagedProjects << stageProject{
    project = 'fabric8-ipaas'
  }

  String fabric8ipaasPR = releaseFabric8 {
    projectStagingDetails = stagedProjects
    project = 'fabric8-ipaas'
  }

  waitUntilArtifactSyncedWithCentral {
    artifact = 'ipaas/distro/distro'
  }

  waitUntilPullRequestMerged{
    name = 'fabric8-ipaas'
    prId = fabric8ipaasPR
  }

  hubot room: 'release', message: "fabric8-ipaas released"
} catch (err){
    hubot room: 'release', message: "fabric8-ipaas release failed ${err}"
    currentBuild.result = 'FAILURE'
}
