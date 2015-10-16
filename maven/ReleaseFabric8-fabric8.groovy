hubot room: 'release', message: "release started"
try {

  releaseProject{
    project = 'fabric8'
    projectArtifact = 'io/fabric8/fabric8-maven-plugin'
  }

  hubot room: 'release', message: "release success"

} catch (err){
    hubot room: 'release', message: "fabric8 release failed ${err}"
    currentBuild.result = 'FAILURE'
}
