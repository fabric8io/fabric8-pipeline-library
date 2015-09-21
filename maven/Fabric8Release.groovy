def updateFabric8ReleaseDeps = ""
try {
  updateFabric8ReleaseDeps = UPDATE_FABRIC8_RELEASE_DEPENDENCIES
} catch (Throwable e) {
  updateFabric8ReleaseDeps = "${env.UPDATE_FABRIC8_RELEASE_DEPENDENCIES ?: 'false'}"
}

def project = 'fabric8'
stage 'canary release ' + project
node {
  ws (project){
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

      def flow = new io.fabric8.Release()

      flow.setupWorkspace 'fabric8io/'+project

      // bump dependency versions from the previous stage
      if (updateFabric8ReleaseDeps == 'true') {
        def kubernetesModelVersion = flow.getReleaseVersion 'kubernetes-model'
        def kubernetesClientVersion = flow.getReleaseVersion 'kubernetes-client'
        flow.searchAndReplaceMavenVersionProperty '<kubernetes-model.version>' kubernetesModelVersion
        flow.searchAndReplaceMavenVersionProperty '<kubernetes-client.version>' kubernetesModelVersion
      }

      flow.release 'release'


  }
}
