def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def project = 'fabric8'
  node {
    ws (project){
      withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

        def flow = new io.fabric8.Release()

        flow.setupWorkspace ('fabric8io/' + project)

        // bump fabric8 release dependency versions
        def kubernetesModelVersion = flow.getReleaseVersion 'kubernetes-model'
        def kubernetesClientVersion = flow.getReleaseVersion 'kubernetes-client'
        flow.searchAndReplaceMavenVersionProperty('<kubernetes-model.version>', kubernetesModelVersion)
        flow.searchAndReplaceMavenVersionProperty('<kubernetes-client.version>', kubernetesClientVersion)

        submitPullRequest{

        }
      }
    }
  }
}
