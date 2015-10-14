def stagedProjects = []

hubot room: 'release', message: "starting ipaas-quickstarts release"
try {

  stage 'bump apps and quickstarts release dependency versions'
  String quickstartPr = bumpiPaaSQuickstartsVersions{}
  if (quickstartPr != null){
    waitUntilPullRequestMerged{
      name = 'ipaas-quickstarts'
      prId = quickstartPr
    }
  }

  stage 'stage ipaas-quickstarts'
  stagedProjects << stageProject{
    project = 'ipaas-quickstarts'
  }

  stage 'release ipaas-quickstarts'
  String quickstartsReleasePR = releaseFabric8 {
    projectStagingDetails = stagedProjects
    project = 'ipaas-quickstarts'
  }

  stage 'wait for artifacts to sync with central'
  waitUntilArtifactSyncedWithCentral {
    artifact = 'archetypes/archetypes-catalog'
  }

  stage 'wait for GitHub pull request merge'
  waitUntilPullRequestMerged{
    name = 'ipaas-quickstarts'
    prId = quickstartsReleasePR
  }

  hubot room: 'release', message: "ipaas-quickstarts released"
} catch (err){
    hubot room: 'release', message: "fabric8 release failed ${err}"
    currentBuild.result = 'FAILURE'
}
