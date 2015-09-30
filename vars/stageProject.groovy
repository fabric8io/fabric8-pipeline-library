def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()


  node {
    ws (config.project){
      withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

        def flow = new io.fabric8.Release()

        flow.setupWorkspaceForRelease(config.project)

        if (config.project == 'fabric8'){
          flow.updateDocsAndSite(flow.getProjectVersion())
        }

        def repoId = flow.stageSonartypeRepo()

        stagedProject.name = config.project
        stagedProject.version = flow.getProjectVersion()
        stagedProject.repoId = repoId

        flow.updateGithub ()

        return stagedProject
      }
    }
  }
}
