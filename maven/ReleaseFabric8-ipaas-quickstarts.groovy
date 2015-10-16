hubot room: 'release', message: "release started"
try {

  releaseProject{
    project = 'ipaas-quickstarts'
    projectArtifact = 'archetypes/archetypes-catalog'
  }

  hubot room: 'release', message: "release success"

} catch (err){
    hubot room: 'release', message: "fabric8 release failed ${err}"
    currentBuild.result = 'FAILURE'
}
