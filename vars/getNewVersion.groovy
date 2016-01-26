#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def version = '1.0.0'

    try {
      // if the repo has no tags this command will fail
      sh 'git describe --tags $(git rev-list --tags --max-count=1) > version.tmp'
      def previousVersion = readFile 'version.tmp'
      echo 'found previous tagged version ' + previousVersion

      try {
        // if there's an int as the version then turn it into a major.minor.micro version
        def myInt = '1' previousVersion int
        return myInt + '.0.1'
      } catch (err) {
        // otherwise lets use the traditional version style
        def microVersion = previousVersion.substring(previousVersion.lastIndexOf('.')+1) as int
        version = previousVersion.substring(0, previousVersion.lastIndexOf('.')+1) + (microVersion+1)
      }
      echo 'using new version ' +  version
    } catch (err) {
      echo "no existing tag found or issue understanding existing tags so using ${version}"
    }
    return version
}
