hubot room: 'release', message: "release started"
try {

  releaseProject{
    project = 'fabric8'
    projectArtifact = 'io/fabric8/fabric8-maven-plugin'
  }

  def proceedMessage = "fabric8 released - would you like to continue?"
  hubotApprove proceedMessage
  input id: 'Proceed', message: "\n${proceedMessage}"

  releaseProject{
    project = 'ipaas-quickstarts'
    projectArtifact = 'io/fabric8/archetypes/archetypes-catalog'
  }

  parallel(devops: {
    releaseProject{
      project = 'fabric8-devops'
      projectArtifact = 'io/fabric8/devops/distro/distro'
    }
  }, ipaas: {
    releaseProject{
      project = 'fabric8-ipaas'
      projectArtifact = 'io/fabric8/ipaas/distro/distro'
    }
  })

  hubot room: 'release', message: "release success"

} catch (err){
    hubot room: 'release', message: "Release failed ${err}"
    currentBuild.result = 'FAILURE'
}
