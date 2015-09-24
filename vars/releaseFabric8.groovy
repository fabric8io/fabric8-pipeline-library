def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  node {
    ws ('fabric8'){
      withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

        def flow = new io.fabric8.Release()

        flow.setupWorkspace ('fabric8io/fabric8')

        // bump dependency versions from the previous stage
        if (config.updateDeps == 'true') {
          def kubernetesModelVersion = flow.getReleaseVersion 'kubernetes-model'
          def kubernetesClientVersion = flow.getReleaseVersion 'kubernetes-client'
          flow.searchAndReplaceMavenVersionProperty('<kubernetes-model.version>', kubernetesModelVersion)
          flow.searchAndReplaceMavenVersionProperty('<kubernetes-client.version>', kubernetesClientVersion)
        }

        //if (flow.hasChangedSinceLastRelease()){
          flow.release "release"
          flow.updateGithub()
        //}
      }
    }
  }
}
