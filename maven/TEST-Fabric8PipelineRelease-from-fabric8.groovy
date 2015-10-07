def release = ""
try {
  release = IS_RELEASE
} catch (Throwable e) {
  release = "${env.IS_RELEASE ?: 'false'}"
}



stagedProject.name = 'fabric8'
stagedProject.version = '2.2.44'
stagedProject.repoId = 'iofabric8-1646'

def stagedProjects = [stagedProject]

stage 'release fabric8'
fabric8ReleasePR = releaseFabric8 {
  projectStagingDetails = stagedProjects
  project = 'fabric8'
}

echo "received ${fabric8ReleasePR}"
stagedProjects = []
