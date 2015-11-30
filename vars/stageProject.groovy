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
        if (config.project == 'fabric8-devops'){
          try {
            def archetypeVersion = flow.getMavenCentralVersion('io/fabric8/archetypes/archetypes-catalog')
            flow.searchAndReplaceMavenVersionProperty("<fabric8.archetypes.release.version>", archetypeVersion)
            updated = true
          } catch (err) {
            echo "Already set archetypes release version dependencies"
          }

          try {
            def projectVersion = flow.getProjectVersion()
            flow.searchAndReplaceMavenVersionProperty("<fabric8.devops.release.version>", projectVersion)
            updated = true
          } catch (err) {
            echo "Already set devops release version dependencies"
          }
        }

        def repoId = flow.stageSonartypeRepo()
        releaseVersion = flow.getProjectVersion()

        stash excludes: '*/src/', includes: '**', name: 'staged'

        flow.updateGithub ()

        return [config.project, releaseVersion, repoId]
      }
    }
  }
}
