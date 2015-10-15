def stagedProjects = []

hubot room: 'release', message: "starting kubernetes client release"
try {
  String clientPullRequest = bumpKubernetesClientVersions{}
  if (clientPullRequest != null){
    waitUntilPullRequestMerged{
      name = 'kubernetes-client'
      prId = clientPullRequest
    }
  }

  stagedProjects << stageProject{
    project = 'kubernetes-client'
  }

  clientReleasePR = releaseFabric8 {
    projectStagingDetails = stagedProjects
    project = 'kubernetes-client'
  }
  
  waitUntilArtifactSyncedWithCentral {
    artifact = 'kubernetes-client'
  }

  hubot room: 'release', message: "Kubernetes Client release was successful"
} catch (err){
    hubot room: 'release', message: "Kubernetes Client release failed ${err}"
    currentBuild.result = 'FAILURE'
}
