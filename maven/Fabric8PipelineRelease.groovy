def updateFabric8ReleaseDeps = ""
try {
  updateFabric8ReleaseDeps = UPDATE_FABRIC8_RELEASE_DEPENDENCIES
} catch (Throwable e) {
  updateFabric8ReleaseDeps = "${env.UPDATE_FABRIC8_RELEASE_DEPENDENCIES ?: 'false'}"
}

def release = ""
try {
  release = IS_RELEASE
} catch (Throwable e) {
  release = "${env.IS_RELEASE ?: 'true'}"
}

stage 'canary release kubernetes-model'
releaseKubernetesModel{
  isRelease = release
}
stage 'wait for kubernetes-model to be synced with maven central'
waitUntilArtifactSyncedWithCentral {
  artifact = 'kubernetes-model'
}

stage 'canary release kubernetes-client'
releaseKubernetesClient{
  updateDeps = updateFabric8ReleaseDeps
  isRelease = release
}

stage 'wait for kubernetes-client to be synced with maven central'
waitUntilArtifactSyncedWithCentral {
  artifact = 'kubernetes-client'
}

stage 'canary release fabric8'
releaseFabric8{
  updateDeps = updateFabric8ReleaseDeps
  isRelease = release
}

stage 'wait for fabric8-maven-plugin to be synced with maven central'
waitUntilArtifactSyncedWithCentral {
  artifact = 'fabric8-maven-plugin'
}

// running parallel builds with only one node doesnt work too well

// stage 'release apps and quickstarts'
// parallel(quickstarts: {
//   releaseiPaaSQuickstarts{
//     updateDeps = updateFabric8ReleaseDeps
//   }
// }, ipaas: {
//   releaseiPaaSApps{
//     updateDeps = updateFabric8ReleaseDeps
//   }
// }, devops:{
//   releaseDevOpsApps{
//     updateDeps = updateFabric8ReleaseDeps
//   }
// }
// )

stage 'release apps and quickstarts'
releaseiPaaSQuickstarts{
  updateDeps = updateFabric8ReleaseDeps
  isRelease = release
}

releaseDevOpsApps{
  updateDeps = updateFabric8ReleaseDeps
  isRelease = release
}

releaseiPaaSApps{
  updateDeps = updateFabric8ReleaseDeps
  isRelease = release
}

stage 'wait for fabric8-maven-plugin to be synced with maven central'
waitUntilArtifactSyncedWithCentral {
  artifact = 'archetypes/archetypes-catalog'
}

stage 'update the docs and website'
updateDocs{
  isRelease = release
}
