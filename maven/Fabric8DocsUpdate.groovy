
def getReleaseVersion(String project) {
  def modelMetaData = new XmlSlurper().parse("https://oss.sonatype.org/content/repositories/releases/io/fabric8/"+project+"/maven-metadata.xml")
  def version = modelMetaData.versioning.release.text()
  return version
}
def oldVersion() {
  def matcher = readFile('website/src/docs/index.page') =~ '<h1>Documentation for version (.+)</h1>'
  matcher ? matcher[0][1] : null
}

// this flow works out the old version from the website then replaces all occurrences with the new version that has been released
stage 'update fabric8 docs'
node {
  ws ('fabric8'){
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
      def project = "fabric8io/fabric8"

      sh "rm -rf *.*"
      git "https://github.com/${project}"
      sh "git remote set-url origin git@github.com:${project}.git"

      sh "git config user.email fabric8-admin@googlegroups.com"
      sh "git config user.name fusesource-ci"

      sh "git checkout master"

      def oldVersion = oldVersion()
      if (oldVersion == null){
        echo "No previous version found"
        return
      }
      echo oldVersion
      def newVersion = getReleaseVersion("fabric8-maven-plugin")

      // use perl so that we we can easily turn off regex in the SED query
      sh "find . -name '*.md' ! -name Changes.md ! -path '*/docs/jube/**.*' | xargs perl -p -i -e 's/\\Q${oldVersion}/${newVersion}/g'"
      sh "find . -path '*/website/src/**.*' | xargs perl -p -i -e 's/\\Q${oldVersion}/${newVersion}/g'"

      sh "git commit -a -m '[CD] Update docs following ${newVersion} release'"
      sh "git push origin master"
    }
  }
}
