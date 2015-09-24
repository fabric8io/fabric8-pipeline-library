def updateFabric8ReleaseDeps = ""
try {
  updateFabric8ReleaseDeps = UPDATE_FABRIC8_RELEASE_DEPENDENCIES
} catch (Throwable e) {
  updateFabric8ReleaseDeps = "${env.UPDATE_FABRIC8_RELEASE_DEPENDENCIES ?: 'false'}"
}

stage 'release ipaas quickstarts'
releaseiPaaSQuickstarts{
  updateDeps = updateFabric8ReleaseDeps
}

stage 'wait for fabric8-maven-plugin to be synced with maven central'
waitUntilArtifactSyncedWithCentral {
  artifact = 'quickstarts/project'
}
