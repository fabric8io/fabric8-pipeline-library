def updateFabric8ReleaseDeps = ""
try {
  updateFabric8ReleaseDeps = UPDATE_FABRIC8_RELEASE_DEPENDENCIES
} catch (Throwable e) {
  updateFabric8ReleaseDeps = "${env.UPDATE_FABRIC8_RELEASE_DEPENDENCIES ?: 'false'}"
}

def project = 'fabric8-devops'

stage 'canary release '+project
node {
  ws project {
    withEnv ["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"] {

      def flow = new io.fabric8.Release()

      flow.setupWorkspace 'fabric8io/'+project

      // bump dependency versions from the previous stage
      if (updateFabric8ReleaseDeps == 'true') {
        def kubernetesModelVersion = flow.getReleaseVersion "fabric8-maven-plugin"
        flow.searchAndReplaceMavenVersionProperty "<fabric8.version>" kubernetesModelVersion
      }

      flow.releaseAndDockerPush "release"
  }
}
