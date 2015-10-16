hubot room: 'release', message: "release started"
try {

  releaseProject{
    project = 'fabric8-devops'
    projectArtifact = 'io/fabric8/devops/distro/distro'
  }

  hubot room: 'release', message: "release success"

} catch (err){
    hubot room: 'release', message: "fabric8-devops release failed ${err}"
    currentBuild.result = 'FAILURE'
}
