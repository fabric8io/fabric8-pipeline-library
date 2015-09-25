package io.fabric8;

def getProjectVersion(){
  def file = readFile('pom.xml')
  def project = new XmlSlurper().parseText(file)
  return project.version.text()
}

def getReleaseVersion(String project) {
  def modelMetaData = new XmlSlurper().parse("https://oss.sonatype.org/content/repositories/releases/io/fabric8/"+project+"/maven-metadata.xml")
  def version = modelMetaData.versioning.release.text()
  return version
}

def getRepoIds() {
  // we could have multiple staging repos created, we need to write the names of all the generated files to a well known
  // filename so we can use the workflow readFile (wildcards wont works and new File wont with slaves as groovy is executed on the master jenkins
  // We write the names of the files that contain the repo ids used for staging.  Each staging repo id is read from each file and returned as a list
  sh 'find target/nexus-staging/staging/  -maxdepth 1 -name "*.properties" > target/nexus-staging/staging/repos.txt'
  def repos = readFile('target/nexus-staging/staging/repos.txt')
  def list = []
  // workflow closure not working here https://issues.jenkins-ci.org/browse/JENKINS-26481
  def filelines = new String(repos).split( '\n' )
  for(int i = 0; i < filelines.size(); i++){
      def matcher = readFile(filelines[i]) =~ 'stagingRepository.id=(.+)'
      list << matcher[0][1]
  }
  return list
}

def getDockerHubImageTags(String image) {
  try {
    return "https://registry.hub.docker.com/v1/repositories/${image}/tags".toURL().getText()
  } catch (err) {
    return "NO_IMAGE_FOUND"
  }
}

def mavenCentralVersion(String artifact) {
  def modelMetaData = new XmlSlurper().parse("http://central.maven.org/maven2/io/fabric8/${artifact}/maven-metadata.xml")
  def version = modelMetaData.versioning.release.text()
  return version
}

def mavenSonartypeReleaseVersion(String artifact) {
  def modelMetaData = new XmlSlurper().parse("https://oss.sonatype.org/content/repositories/releases/io/fabric8/${artifact}/maven-metadata.xml")
  def version = modelMetaData.versioning.release.text()
  return version
}

def searchAndReplaceMavenVersionProperty(String property, String newVersion){
  try {
    sh "find -type f -name 'pom.xml' | xargs sed -i -r 's/${property}[0-9][0-9]{0,2}.[0-9][0-9]{0,2}.[0-9][0-9]{0,2}.([0-9][0-9]{0,2})?</${property}${newVersion}</g'"
    sh "git commit -a -m 'Bump ${property} version'"
  } catch (err) {
    echo "Already on the latest versions of fabric8 dependencies"
  }
}

def searchAndReplaceMavenSnapshotProfileVersionProperty(String property, String newVersion){
  try {
    sh "find -type f -name 'pom.xml' | xargs sed -i -r 's/${property}[0-9][0-9]{0,2}.[0-9][0-9]{0,2}.[0-9][0-9]{0,2}-SNAPSHOT</${property}${newVersion}-SNAPSHOT</g'"
    sh "git commit -a -m 'Bump ${property} development profile SNAPSHOT version'"
  } catch (err) {
    echo "Already on the latest SNAPSHOT versions of fabric8 dependencies"
  }
}

def setupWorkspace(String project){
  sh "rm -rf *.*"
  git "https://github.com/${project}"
  sh "git remote set-url origin git@github.com:${project}.git"

  sh "git config user.email fabric8-admin@googlegroups.com"
  sh "git config user.name fusesource-ci"

  sh "git checkout master"

  sh "git tag -d \$(git tag)"
  sh "git fetch --tags"
  sh "git reset --hard origin/master"

  // lets avoid using the maven release plugin so we have more control over the release
  sh 'mvn build-helper:parse-version versions:set -DnewVersion=\\\${parsedVersion.majorVersion}.\\\${parsedVersion.minorVersion}.\\\${parsedVersion.incrementalVersion}'
  def releaseVersion = getProjectVersion()
  sh "git commit -a -m '[CD] released v${releaseVersion}'"
}

def dockerPush (String commaDelimetedProfiles, String isRelease) {
  if (isRelease == 'true') {
    // intermittent errors can occur when pushing to dockerhub
    retry(3){
      sh "mvn docker:push -P ${commaDelimetedProfiles}"
    }
  } else {
    echo "Not a release so not pushing to dockerhub"
  }
}

def release (String commaDelimetedProfiles, String isRelease) {
  retry(3){
    sh "mvn -V -B -U clean install org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:deploy -P ${commaDelimetedProfiles} -DnexusUrl=https://oss.sonatype.org -DserverId=oss-sonatype-staging"
  }
  // the sonartype staging repo id gets written to a file in the workspace
  def repoIds = getRepoIds()

  if (isRelease == 'true') {
    try {
      // release the sonartype staging repo
      for(int i = 0; i < repoIds.size(); i++){
        sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoIds[i]} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
      }

    } catch (err) {
      for(int i = 0; i < repoIds.size(); i++){
        sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-drop -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoIds[i]} -Ddescription=\"Error during release: ${err}\" -DstagingProgressTimeoutMinutes=60"
        currentBuild.result = 'FAILURE'
      }
      return
    }
  } else {
    echo "Not a release so dropping staging repos"
    for(int i = 0; i < repoIds.size(); i++){
      sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-drop -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoIds[i]} -Ddescription=\"Dry run\" -DstagingProgressTimeoutMinutes=60"
    }
  }
}

def updateGithub(String isRelease){
  // push release versions and tag it
  def releaseVersion = getProjectVersion()
  sh "git push origin master"
  sh "git tag -a v${releaseVersion} -m 'Release version ${releaseVersion}'"
  sh "git push origin v${releaseVersion}"

  // update poms back to snapshot again
  sh 'mvn build-helper:parse-version versions:set -DnewVersion=\\\${parsedVersion.majorVersion}.\\\${parsedVersion.minorVersion}.\\\${parsedVersion.nextIncrementalVersion}-SNAPSHOT'
  def snapshotVersion = getProjectVersion()
  sh "git commit -a -m '[CD] prepare for next development iteration ${snapshotVersion}'"

  if (isRelease == 'true') {
    sh "git push origin master"
  } else {
    echo "Not a release so changes will not be pushed to GitHub"
  }
}

def hasChangedSinceLastRelease(){
  sh "git log --name-status HEAD^..HEAD -1 --grep=\"prepare for next development iteration\" --author='fusesource-ci' >> gitlog.tmp"
  def myfile = readFile('gitlog.tmp')
  //sh "rm gitlog.tmp"
  // if the CI user was the last commit then this project has not changed
  if (myfile.length() > 0 ) {
    return false
  } else {
    return true
  }
}

def getOldVersion(){
  def matcher = readFile('website/src/docs/index.page') =~ '<h1>Documentation for version (.+)</h1>'
  matcher ? matcher[0][1] : null
}

return this;
