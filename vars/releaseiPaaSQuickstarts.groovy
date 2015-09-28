def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  node {
    ws ('ipaas-quickstarts'){
      withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

        def flow = new io.fabric8.Release()

        flow.setupWorkspace ('fabric8io/ipaas-quickstarts')

        // bump dependency versions from the previous stage
        if (config.updateDeps == 'true') {
          def kubernetesModelVersion = flow.getReleaseVersion "fabric8-maven-plugin"
          flow.searchAndReplaceMavenVersionProperty("<fabric8.version>", kubernetesModelVersion)

          def parsedVersion = kubernetesModelVersion.split('\\.')
          def nextFabric8DevelopmentSnapshotVersion = (parsedVersion[2].toInteger() + 1)
          flow.searchAndReplaceMavenSnapshotProfileVersionProperty("<fabric8.version>", parsedVersion[0] + '.' + parsedVersion[1] + '.' + nextFabric8DevelopmentSnapshotVersion)
        }

        //if (flow.hasChangedSinceLastRelease()){
        flow.release ("release,archetypes", config.isRelease)
        flow.updateGithub(config.isRelease)
        //}
      }
    }
  }
}
