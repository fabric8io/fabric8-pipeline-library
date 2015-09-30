def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def project = 'fabric8-devops'
  node {
    ws (project){
      withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

        def flow = new io.fabric8.Release()
        flow.setupWorkspace (project)

        def uid = UUID.randomUUID().toString()
        sh "git checkout -b versionUpdate${uid}"

        def fabric8Version = flow.getReleaseVersion "fabric8-maven-plugin"
        flow.searchAndReplaceMavenVersionProperty("<fabric8.version>", fabric8Version)

        def parsedVersion = fabric8Version.split('\\.')
        def nextFabric8DevelopmentSnapshotVersion = (parsedVersion[2].toInteger() + 1)
        flow.searchAndReplaceMavenSnapshotProfileVersionProperty("<fabric8.version>", parsedVersion[0] + '.' + parsedVersion[1] + '.' + nextFabric8DevelopmentSnapshotVersion)

        sh "git push origin versionUpdate${uid}"
        return flow.createPullRequest("[CD] Update release dependencies")
      }
    }
  }
}
