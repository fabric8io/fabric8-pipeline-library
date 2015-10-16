hubot room: 'release', message: "release started"
try {

  releaseProject{
    project = 'kubernetes-model'
    projectArtifact = 'kubernetes-model'
  }

  hubot room: 'release', message: "release success"

} catch (err){
    hubot room: 'release', message: "Kubernetes Model release failed ${err}"
    currentBuild.result = 'FAILURE'
}
