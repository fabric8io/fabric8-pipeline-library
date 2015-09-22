def updateFabric8ReleaseDeps = ""
try {
  updateFabric8ReleaseDeps = UPDATE_FABRIC8_RELEASE_DEPENDENCIES
} catch (Throwable e) {
  updateFabric8ReleaseDeps = "${env.UPDATE_FABRIC8_RELEASE_DEPENDENCIES ?: 'false'}"
}

stage 'wait for kubernetes-client to be synced with maven central'
waitUntilArtifactSyncedWithCentral {
  artifact = 'kubernetes-client'
}

stage 'canary release fabric8'
releaseFabric8{
  updateDeps = updateFabric8ReleaseDeps
}

stage 'wait for fabric8-maven-plugin to be synced with maven central'
waitUntilArtifactSyncedWithCentral {
  artifact = 'fabric8-maven-plugin'
}
