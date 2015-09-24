def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    // now build, based on the configuration provided
    node {
      ws ('fabric8'){
        withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

          def flow = new io.fabric8.Release()
          def project = 'fabric8io/fabric8'

          sh "rm -rf *.*"
          git "https://github.com/${project}"
          sh "git remote set-url origin git@github.com:${project}.git"

          sh "git config user.email fabric8-admin@googlegroups.com"
          sh "git config user.name fusesource-ci"

          sh "git checkout master"

          sh "git tag -d \$(git tag)"
          sh "git fetch --tags"
          sh "git reset --hard origin/master"

          // get previous version
          def oldVersion = flow.getOldVersion()

          if (oldVersion == null){
            echo "No previous version found"
            return
          }
          def newVersion = flow.getReleaseVersion "fabric8-maven-plugin"

          // use perl so that we we can easily turn off regex in the SED query as using dots in version numbers returns unwanted results otherwise
          sh "find . -name '*.md' ! -name Changes.md ! -path '*/docs/jube/**.*' | xargs perl -p -i -e 's/\\Q${oldVersion}/${newVersion}/g'"
          sh "find . -path '*/website/src/**.*' | xargs perl -p -i -e 's/\\Q${oldVersion}/${newVersion}/g'"

          sh "git commit -a -m '[CD] Update docs following ${newVersion} release'"
          sh "git push origin master"
       }
      }
    }
}
