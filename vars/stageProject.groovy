def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  stage "stage ${config.project}"
  node ('swarm'){
    ws (config.project){
      withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

        def flow = new io.fabric8.Release()

        flow.setupWorkspaceForRelease(config.project)

        if (config.project == 'fabric8'){
          flow.updateDocsAndSite(flow.getProjectVersion())
        }

        def repoId = flow.stageSonartypeRepo()
        releaseVersion = flow.getProjectVersion()
        flow.updateGithub ()

        return [config.project, releaseVersion, repoId]
      }
    }
  }
}
