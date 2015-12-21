def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def project = 'fabric8-devops'
  stage "bump ${project} versions"
  node ('kubernetes'){
    ws (project){
      withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

        def flow = new io.fabric8.Fabric8Commands()
        flow.setupWorkspace (project)

        def uid = UUID.randomUUID().toString()
        sh "git checkout -b versionUpdate${uid}"
        def fabric8Version = flow.getReleaseVersion "io/fabric8/fabric8-maven-plugin"
        def updated = false
        try {
          flow.searchAndReplaceMavenVersionProperty("<fabric8.version>", fabric8Version)
          updated = true
        } catch (err) {
          echo "Already on the latest versions of fabric8 dependencies"
        }

        try {
          def parsedVersion = fabric8Version.split('\\.')
          def nextFabric8DevelopmentSnapshotVersion = (parsedVersion[2].toInteger() + 1)
          flow.searchAndReplaceMavenSnapshotProfileVersionProperty("<fabric8.version>", parsedVersion[0] + '.' + parsedVersion[1] + '.' + nextFabric8DevelopmentSnapshotVersion)
          updated = true
        } catch (err) {
          echo "Already on the latest SNAPSHOT versions of fabric8 dependencies"
        }

        // only make a pull request if we've updated a version
        if (updated) {
          sh "git push origin versionUpdate${uid}"
          prId = flow.createPullRequest("[CD] Update release dependencies","${project}")
          echo "received pull request id ${prId}"
          return prId
        } else {
          message = "fabric8-devops already on the latest release versions"
          hubot room: 'release', message: message
          return
        }
      }
    }
  }
}
