hubot room: 'release', message: "release started"
try {

  releaseProject{
    project = 'kubernetes-client'
    projectArtifact = 'kubernetes-client'
  }

  hubot room: 'release', message: "release success"

} catch (err){
    hubot room: 'release', message: "Kubernetes Client release failed ${err}"
    currentBuild.result = 'FAILURE'
}
