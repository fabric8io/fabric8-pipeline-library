def stagedProjects = []

hubot room: 'release', message: "starting ipaas-quickstarts release"
try {

  String quickstartPr = bumpiPaaSQuickstartsVersions{}
  if (quickstartPr != null){
    waitUntilPullRequestMerged{
      name = 'ipaas-quickstarts'
      prId = quickstartPr
    }
  }

  stagedProjects << stageProject{
    project = 'ipaas-quickstarts'
  }

  String quickstartsReleasePR = releaseFabric8 {
    projectStagingDetails = stagedProjects
    project = 'ipaas-quickstarts'
  }

  waitUntilArtifactSyncedWithCentral {
    artifact = 'archetypes/archetypes-catalog'
  }

  waitUntilPullRequestMerged{
    name = 'ipaas-quickstarts'
    prId = quickstartsReleasePR
  }

  hubot room: 'release', message: "ipaas-quickstarts released"
} catch (err){
    hubot room: 'release', message: "fabric8 release failed ${err}"
    currentBuild.result = 'FAILURE'
}
