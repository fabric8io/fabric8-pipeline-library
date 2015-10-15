def stagedProjects = []

hubot room: 'release', message: "starting Kubernetes Model release"
try {
  stagedProjects << stageProject{
    project = 'kubernetes-model'
  }

  modelReleasePR = releaseFabric8 {
     projectStagingDetails = stagedProjects
     project = 'kubernetes-model'
  }

  waitUntilArtifactSyncedWithCentral {
    artifact = 'kubernetes-model'
  }

  hubot room: 'release', message: "Kubernetes Model release was successful"

  String clientPullRequest = bumpKubernetesClientVersions{}
  if (clientPullRequest != null){
    waitUntilPullRequestMerged{
      name = 'kubernetes-client'
      prId = clientPullRequest
    }
  }

  hubot room: 'release', message: "Kubernetes Model version updated in Kubernetes Client"
} catch (err){
    hubot room: 'release', message: "Kubernetes Model release failed ${err}"
    currentBuild.result = 'FAILURE'
}
