def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  node {
    ws ('fabric8-devops'){
      withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

        def flow = new io.fabric8.Release()

        flow.setupWorkspace ('fabric8io/fabric8-devops')

        // bump dependency versions from the previous stage
        if (config.updateDeps == 'true') {
          def fabric8Version = flow.getReleaseVersion "fabric8-maven-plugin"
          flow.searchAndReplaceMavenVersionProperty("<fabric8.version>", fabric8Version)

          def archetypesReleaseVersion = flow.getReleaseVersion "archetypes/archetypes-catalog"
          flow.searchAndReplaceMavenVersionProperty("<fabric8.archetypes.release.version>", archetypesReleaseVersion)

          def devopsVersion = flow.getProjectVersion()
          flow.searchAndReplaceMavenVersionProperty("<fabric8.devops.release.version>", devopsVersion)

          def parsedVersion = fabric8Version.split('\\.')
          def nextFabric8DevelopmentSnapshotVersion = (parsedVersion[2].toInteger() + 1)
          flow.searchAndReplaceMavenSnapshotProfileVersionProperty("<fabric8.version>", parsedVersion[0] + '.' + parsedVersion[1] + '.' + nextFabric8DevelopmentSnapshotVersion)
        }

        //if (flow.hasChangedSinceLastRelease()){
          flow.release "release,quickstarts"
          flow.dockerPush "release,quickstarts"
          flow.updateGithub()
        //}
      }
    }
  }
}
