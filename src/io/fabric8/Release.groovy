package io.fabric8;

import groovy.json.JsonSlurper

def getGitRepo(){
  'rawlingsj'
}

def getProjectVersion(){
  def file = readFile('pom.xml')
  def project = new XmlSlurper().parseText(file)
  return project.version.text()
}

def getReleaseVersion(String project) {
  def modelMetaData = new XmlSlurper().parse("https://oss.sonatype.org/content/repositories/releases/io/fabric8/$project/maven-metadata.xml")
  def version = modelMetaData.versioning.release.text()
  return version
}

def getPullRequestState(String project, String id){
  def gitRepo = getGitRepo()
  def pr = new JsonSlurper().parse("https://api.github.com/repos/${gitRepo}/${project}/pulls/${id}")
  return pr.state
}

def getBranchName(String project, String id){
  def gitRepo = getGitRepo()
  def pr = new JsonSlurper().parse("https://api.github.com/repos/${gitRepo}/${project}/pulls/${id}")
  return pr.head.ref
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
  // example matches <fabric8.version>2.3</fabric8.version> <fabric8.version>2.3.12</fabric8.version> <fabric8.version>2.3.12.5</fabric8.version>
  try {
    sh "find -type f -name 'pom.xml' | xargs sed -i -r 's/${property}[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2})?(.[0-9][0-9]{0,2})?</${property}${newVersion}</g'"
    sh "git commit -a -m 'Bump ${property} version'"
  } catch (err) {
    echo "Already on the latest versions of fabric8 dependencies"
  }
}

def searchAndReplaceMavenSnapshotProfileVersionProperty(String property, String newVersion){
  // example matches <fabric8.version>2.3-SNAPSHOT</fabric8.version> <fabric8.version>2.3.12-SNAPSHOT</fabric8.version> <fabric8.version>2.3.12.5-SNAPSHOT</fabric8.version>
  try {
    sh "find -type f -name 'pom.xml' | xargs sed -i -r 's/${property}[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2})?(.[0-9][0-9]{0,2})?-SNAPSHOT</${property}${newVersion}-SNAPSHOT</g'"
    sh "git commit -a -m 'Bump ${property} development profile SNAPSHOT version'"
  } catch (err) {
    echo "Already on the latest SNAPSHOT versions of fabric8 dependencies"
  }
}

def setupWorkspace(String project){
  def gitRepo = getGitRepo()
  sh "rm -rf *.* .git"
  git "https://github.com/${gitRepo}/${project}"
  sh "git remote set-url origin git@github.com:${gitRepo}/${project}.git"

  sh "git config user.email fabric8-admin@googlegroups.com"
  sh "git config user.name fusesource-ci"

  sh "git checkout master"
}

def setupWorkspaceForRelease(String project){
  setupWorkspace (project)


  sh "git tag -d \$(git tag)"
  sh "git fetch --tags"
  sh "git reset --hard origin/master"

  sh 'mvn build-helper:parse-version versions:set -DnewVersion=\\\${parsedVersion.majorVersion}.\\\${parsedVersion.minorVersion}.\\\${parsedVersion.incrementalVersion}'
  def releaseVersion = getProjectVersion()

  sh "git checkout -b release-v${releaseVersion}"
  
  sh "git commit -a -m '[CD] released v${releaseVersion}'"
}

def dockerPush () {
  // intermittent errors can occur when pushing to dockerhub
  retry(3){
    sh "mvn docker:push -P release"
  }
}

def stageSonartypeRepo () {
  retry(3){
    sh "mvn -V -B -U clean install org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:deploy -P release -DnexusUrl=https://oss.sonatype.org -DserverId=oss-sonatype-staging"
  }
  // the sonartype staging repo id gets written to a file in the workspace
  return getRepoIds()
}

def releaseSonartypeRepo(String repoId){
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
}

def dropRelease(String id){
  echo "Not a release so dropping staging repo ${id}"
  sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-drop -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoIds[i]} -Ddescription=\"Dry run\" -DstagingProgressTimeoutMinutes=60"
}

def updateGithub(){
  // push release versions and tag it
  def releaseVersion = getProjectVersion()

  sh "git tag -a v${releaseVersion} -m 'Release version ${releaseVersion}'"
  sh "git push origin release-v${releaseVersion}"

  // update poms back to snapshot again
  sh 'mvn build-helper:parse-version versions:set -DnewVersion=\\\${parsedVersion.majorVersion}.\\\${parsedVersion.minorVersion}.\\\${parsedVersion.nextIncrementalVersion}-SNAPSHOT'
  def snapshotVersion = getProjectVersion()
  sh "git commit -a -m '[CD] prepare for next development iteration ${snapshotVersion}'"

  sh "git push origin release-v${releaseVersion}"
}

def hasChangedSinceLastRelease(){
  sh "git log --name-status HEAD^..HEAD -1 --grep=\"prepare for next development iteration\" --author='fusesource-ci' >> gitlog.tmp"
  def myfile = readFile('gitlog.tmp')
  //sh "rm gitlog.tmp"
  // if the file size is 0 it means the CI user was not the last commit so project has changed
  if (myfile.length() == 0 ) {
    return true
  } else {
    return false
  }
}

def getOldVersion(){
  def matcher = readFile('website/src/docs/index.page') =~ '<h1>Documentation for version (.+)</h1>'
  matcher ? matcher[0][1] : null
}

def updateDocsAndSite(String newVersion){
  // get previous version
  def oldVersion = flow.getOldVersion()

  if (oldVersion == null){
    echo "No previous version found"
    return
  }

  // use perl so that we we can easily turn off regex in the SED query as using dots in version numbers returns unwanted results otherwise
  sh "find . -name '*.md' ! -name Changes.md ! -path '*/docs/jube/**.*' | xargs perl -p -i -e 's/\\Q${oldVersion}/${newVersion}/g'"
  sh "find . -path '*/website/src/**.*' | xargs perl -p -i -e 's/\\Q${oldVersion}/${newVersion}/g'"

  sh "git commit -a -m '[CD] Update docs following ${newVersion} release'"

}

def runSystemTests(){
  sh 'cd systests && mvn clean integration-test verify'
}

def createPullRequest(String message){
  sh 'hub pull-request -m \"${message}\" > pr.txt'
  def pr = readFile(pr.txt)
  sh 'rm pr.txt'
  def split = pr.split('\\/')
  return split[7]
}

def deleteRemoteBranch(String branchName){
  sh "git push origin --delete ${branchName}"
}

return this;
