def stagedProjects = []

hubot room: 'release', message: "starting kubernetes client release"
try {

  stage 'stage kubernetes-model'
  stagedProjects << stageProject{
    project = 'kubernetes-model'
  }

  stage 'release kubernetes-model'
  modelReleasePR = release {
     projectStagingDetails = stagedProjects
     project = 'kubernetes-model'
  }
  waitUntilArtifactSyncedWithCentral {
    artifact = 'kubernetes-model'
  }
  stagedProjects = []

  stage 'update kubernetes-client release dependency versions'
  String clientPullRequest = bumpKubernetesClientVersions{}
  if (clientPullRequest != null){
    waitUntilPullRequestMerged{
      name = 'kubernetes-client'
      prId = clientPullRequest
    }
  }

  stage 'stage kubernetes-client'
  stagedProjects << stageProject{
    project = 'kubernetes-client'
  }

  stage 'release kubernetes-client'
  clientReleasePR = releaseFabric8 {
    projectStagingDetails = stagedProjects
    project = 'kubernetes-client'
  }
  stagedProjects = []
  waitUntilArtifactSyncedWithCentral {
    artifact = 'kubernetes-client'
  }

  hubot room: 'release', message: "Kubernetes Client release was successful"
} catch (err){
    hubot room: 'release', message: "Kubernetes Client release failed ${err}"
    currentBuild.result = 'FAILURE'
}
