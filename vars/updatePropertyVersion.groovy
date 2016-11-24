#!/usr/bin/groovy
def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

    def flow = new io.fabric8.Fabric8Commands()

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
    def rs
    if (updated) {

      container(name: 'maven') {

        sh 'chmod 600 /root/.ssh-git/ssh-key'
        sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
        sh 'chmod 700 /root/.ssh-git'

        sh "git push origin versionUpdate${uid}"

        rs = flow.createPullRequest("[CD] Update release dependencies","${config.project}","versionUpdate${uid}")

      }
      return rs
    }

}
