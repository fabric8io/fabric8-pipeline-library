def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  stage "stage ${config.project}"
  node ('kubernetes'){
    ws (config.project){
      withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

        def flow = new io.fabric8.Fabric8Commands()
        flow.setupWorkspaceForRelease(config.project)

        if (config.project == 'fabric8'){
          flow.updateDocsAndSite(flow.getProjectVersion())
        }

        // update project specific properties
        if (config.project == 'fabric8-forge'){
          try {
            def archetypeVersion = flow.getMavenCentralVersion('io/fabric8/archetypes/archetypes-catalog')
            flow.searchAndReplaceMavenVersionProperty("<fabric8.archetypes.release.version>", archetypeVersion)
            updated = true
          } catch (err) {
            echo "Already set archetypes release version dependencies"
          }

          try {
            def devopsVersion = flow.getMavenCentralVersion('io/fabric8/devops/apps/jenkins')
            flow.searchAndReplaceMavenVersionProperty("<fabric8.devops.version>", devopsVersion)
            updated = true
          } catch (err) {
            echo "Already set devops release version dependencies"
          }
        }

        def repoId = flow.stageSonartypeRepo()
        releaseVersion = flow.getProjectVersion()

        stash excludes: '*/src/', includes: '**', name: "staged-${config.project}-${releaseVersion}"

        flow.updateGithub ()

        return [config.project, releaseVersion, repoId]
      }
    }
  }
}
