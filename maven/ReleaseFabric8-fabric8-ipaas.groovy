def stagedProjects = []

hubot room: 'release', message: "starting fabric8-ipaas release"
try {
  stage 'bump fabric8-ipaas release dependency versions'
  String ipaasPr = bumpFabric8iPaaSVersions{}
  if (ipaasPr != null){
    waitUntilPullRequestMerged{
      name = 'fabric8-ipaas'
      prId = ipaasPr
    }
  }

  stage 'stage fabric8-ipaas'
  stagedProjects << stageProject{
    project = 'fabric8-ipaas'
  }

  stage 'release fabric8-ipaas'
  String fabric8ipaasPR = releaseFabric8 {
    projectStagingDetails = stagedProjects
    project = 'fabric8-ipaas'
  }

  stage 'wait for artifacts to sync with central'
  waitUntilArtifactSyncedWithCentral {
    artifact = 'ipaas/distro/distro'
  }

  stage 'wait for GitHub pull request merge'
  waitUntilPullRequestMerged{
    name = 'fabric8-ipaas'
    prId = fabric8ipaasPR
  }

  hubot room: 'release', message: "fabric8-ipaas released"
} catch (err){
    hubot room: 'release', message: "fabric8-ipaas release failed ${err}"
    currentBuild.result = 'FAILURE'
}
