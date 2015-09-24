def updateFabric8ReleaseDeps = ""
try {
  updateFabric8ReleaseDeps = UPDATE_FABRIC8_RELEASE_DEPENDENCIES
} catch (Throwable e) {
  updateFabric8ReleaseDeps = "${env.UPDATE_FABRIC8_RELEASE_DEPENDENCIES ?: 'false'}"
}

stage 'release apps and quickstarts'
parallel(quickstarts: {
  releaseFabric8{
    updateDeps = updateFabric8ReleaseDeps
  }
}, ipaas: {
  releaseiPaaSApps{
    updateDeps = updateFabric8ReleaseDeps
  }
}, devops:{
  releaseiPaaSQuickstarts{
    updateDeps = updateFabric8ReleaseDeps
  }
}

stage 'wait for fabric8-maven-plugin to be synced with maven central'
waitUntilArtifactSyncedWithCentral {
  artifact = 'quickstarts/project'
}
