#!/usr/bin/groovy
def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  stage "dependency updates"

    def flow = new io.fabric8.Fabric8Commands()
    //flow.setupWorkspace (project)
    // sh "git config user.email fabric8-admin@googlegroups.com"
    // sh "git config user.name fusesource-ci"
    //
    // sh "git checkout master"

    sh "git config user.email fabric8-admin@googlegroups.com"
    sh "git config user.name fabric8-cd"

    def uid = UUID.randomUUID().toString()
    sh "git checkout -b versionUpdate${uid}"

    def updated
    // lets default to using maven central to get latest artifact version if no source URL present
    def versionRepository = config.repository ?: 'http://central.maven.org/maven2/'

    for(int j = 0; j < config.updates.size(); j++){

      def property = config.updates[j][0]
      def version = flow.getVersion(versionRepository, config.updates[j][1])

      try {
        flow.searchAndReplaceMavenVersionProperty(property, version)
        updated = true
      } catch (err) {
        echo "${err}"
        echo "Already on the latest versions of the ${property} dependency"
      }
    }

    // only make a pull request if we've updated a version
    if (updated) {
      sh "git push origin versionUpdate${uid}"
      return flow.createPullRequest("[CD] Update release dependencies","${config.project}")
    }

}
