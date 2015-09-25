def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  node {
    ws ('kubernetes-client'){
      withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

        def flow = new io.fabric8.Release()

        flow.setupWorkspace ('fabric8io/kubernetes-client')

        // bump dependency versions from the previous stage
        if (config.updateDeps == 'true') {
          def kubernetesModelVersion = flow.getReleaseVersion "kubernetes-model"
          flow.searchAndReplaceMavenVersionProperty("<kubernetes.model.version>", kubernetesModelVersion)
        }
        echo "is a release ${config.isRelease}"
        echo "is a release ${config.isRelease}"
        //if (flow.hasChangedSinceLastRelease()){
        flow.release ("release", config.isRelease)
        flow.updateGithub config.isRelease
        //}
      }
    }
  }
}
