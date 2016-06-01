#!/usr/bin/groovy
package io.fabric8
import groovy.json.JsonSlurper

def getProjectVersion(){
  def file = readFile('pom.xml')
  def project = new XmlSlurper().parseText(file)
  return project.version.text()
}

def getReleaseVersion(String artifact) {
  def modelMetaData = new XmlSlurper().parse("https://oss.sonatype.org/content/repositories/releases/${artifact}/maven-metadata.xml")
  def version = modelMetaData.versioning.release.text()
  return version
}

def getMavenCentralVersion(String artifact) {
  def modelMetaData = new XmlSlurper().parse("http://central.maven.org/maven2/${artifact}/maven-metadata.xml")
  def version = modelMetaData.versioning.release.text()
  return version
}

def getVersion(String repo, String artifact) {
  repo = removeTrailingSlash(repo)
  artifact = removeTrailingSlash(artifact)

  def modelMetaData = new XmlSlurper().parse(repo+'/'+artifact+'/maven-metadata.xml')
  def version = modelMetaData.versioning.release.text()
  return version
}

def isArtifactAvailableInRepo(String repo, String groupId, String artifactId, String version, String ext) {
  repo = removeTrailingSlash(repo)
  groupId = removeTrailingSlash(groupId)
  artifactId = removeTrailingSlash(artifactId)

  def url = new URL("${repo}/${groupId}/${artifactId}/${version}/${artifactId}-${version}.${ext}")
  def HttpURLConnection connection = url.openConnection()

  connection.setRequestMethod("GET")
  connection.setDoInput(true)

  try {
    connection.connect()
    new InputStreamReader(connection.getInputStream(),"UTF-8")
    return true
  } catch( FileNotFoundException e1 ) {
    echo "File not yet available: ${url.toString()}"
    return false
  } finally {
    connection.disconnect()
  }
}

def removeTrailingSlash (String myString){
  if (myString.endsWith("/")) {
    return myString.substring(0, myString.length() - 1);
  }
  return myString
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

def searchAndReplaceMavenVersionProperty(String property, String newVersion){
  // example matches <fabric8.version>2.3</fabric8.version> <fabric8.version>2.3.12</fabric8.version> <fabric8.version>2.3.12.5</fabric8.version>
  sh "find -type f -name 'pom.xml' | xargs sed -i -r 's/${property}[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2})?(.[0-9][0-9]{0,2})?</${property}${newVersion}</g'"
  sh "git commit -a -m 'Bump ${property} version'"
}

def searchAndReplaceMavenSnapshotProfileVersionProperty(String property, String newVersion){
  // example matches <fabric8.version>2.3-SNAPSHOT</fabric8.version> <fabric8.version>2.3.12-SNAPSHOT</fabric8.version> <fabric8.version>2.3.12.5-SNAPSHOT</fabric8.version>
  sh "find -type f -name 'pom.xml' | xargs sed -i -r 's/${property}[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2})?(.[0-9][0-9]{0,2})?-SNAPSHOT</${property}${newVersion}-SNAPSHOT</g'"
  sh "git commit -a -m 'Bump ${property} development profile SNAPSHOT version'"
}

def setupWorkspaceForRelease(String project, Boolean useGitTagForNextVersion, String mvnExtraArgs = ""){
  sh "git config user.email fabric8-admin@googlegroups.com"
  sh "git config user.name fabric8-release"

  sh "git tag -d \$(git tag)"
  sh "git fetch --tags"

  if (useGitTagForNextVersion){
    def newVersion = getNewVersionFromTag()
    echo "New release version ${newVersion}"
    sh "mvn versions:set -DnewVersion=${newVersion} " + mvnExtraArgs
    sh "git commit -a -m 'release ${newVersion}'"
    pushTag(newVersion)
  } else {
    sh 'mvn build-helper:parse-version versions:set -DnewVersion=\\\${parsedVersion.majorVersion}.\\\${parsedVersion.minorVersion}.\\\${parsedVersion.nextIncrementalVersion} ' + mvnExtraArgs
  }

  def releaseVersion = getProjectVersion()

  // delete any previous branches of this release
  try {
    sh "git checkout -b release-v${releaseVersion}"
  } catch (err){
    sh "git branch -D release-v${releaseVersion}"
    sh "git checkout -b release-v${releaseVersion}"
  }

  try {
    deleteRemoteBranch("release-v${releaseVersion}")
  } catch (err){
  }

  //sh "git commit -a -m '[CD] released v${releaseVersion}'"
}

// if no previous tag found default 1.0.0 is used, else assume version is in the form major.minor or major.minor.micro version
def getNewVersionFromTag(){
  def version = '0.0.1'

  // Set known prerelease prefixes, needed for the proper sort order
  // in the next command
  sh "git config versionsort.prereleaseSuffix -RC"
  sh "git config versionsort.prereleaseSuffix -M"

  // if the repo has no tags this command will fail
  sh "git tag --sort version:refname | tail -1 > version.tmp"

  def tag = readFile 'version.tmp'

  if (tag == null || tag.size() == 0){
    echo "no existing tag found using version ${version}"
    return version
  }

  tag = tag.trim()

  // strip the v prefix from the tag so we can use in a maven version number
  def previousReleaseVersion = tag.substring(tag.lastIndexOf('v')+1)
  echo "Previous version found ${previousReleaseVersion}"

  // if there's an int as the version then turn it into a major.minor.micro version
  if (previousReleaseVersion.isNumber()){
    return previousReleaseVersion + '.0.1'
  } else {
    // if previous tag is not a number and doesnt have a '.' version seperator then error until we have one
    if (previousReleaseVersion.lastIndexOf('.') == 0){
      error "found invalid latest tag [${previousReleaseVersion}] set to major.minor.micro to calculate next release version"
    }
    // incrememnt the release number after the last seperator '.'
    def microVersion = previousReleaseVersion.substring(previousReleaseVersion.lastIndexOf('.')+1) as int
    return previousReleaseVersion.substring(0, previousReleaseVersion.lastIndexOf('.')+1) + (microVersion+1)
  }
}

def stageSonartypeRepo () {
  try {
    sh "mvn -V -B -U clean install org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:deploy -P release -DnexusUrl=https://oss.sonatype.org -DserverId=oss-sonatype-staging -Ddocker.registry=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}"
  } catch (err) {
    hubot room: 'release', message: "Release failed when building and deploying to Nexus ${err}"
    currentBuild.result = 'FAILURE'
  }
  // the sonartype staging repo id gets written to a file in the workspace
  return getRepoIds()
}

def releaseSonartypeRepo(String repoId){
  try {
    // release the sonartype staging repo
    sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"

  } catch (err) {
    sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-drop -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Error during release: ${err}\" -DstagingProgressTimeoutMinutes=60"
    currentBuild.result = 'FAILURE'
    return
  }
}

def dropStagingRepo(String repoId){
  echo "Not a release so dropping staging repo ${repoId}"
  sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-drop -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Dry run\" -DstagingProgressTimeoutMinutes=60"
}

def helm(){
  def pluginVersion = getReleaseVersion("io/fabric8/fabric8-maven-plugin")
  try {
    sh "mvn io.fabric8:fabric8-maven-plugin:${pluginVersion}:helm"
    sh "mvn io.fabric8:fabric8-maven-plugin:${pluginVersion}:helm-push"
  } catch (err) {
    echo "ERROR with helm push ${err}"
    return
  }
}

def pushTag(String releaseVersion){
  sh "git tag -fa v${releaseVersion} -m 'Release version ${releaseVersion}'"
  sh "git push origin v${releaseVersion}"
}


def updateGithub(){
  def releaseVersion = getProjectVersion()
  sh "git push origin release-v${releaseVersion}"
}

def updateNextDevelopmentVersion(String releaseVersion, String mvnExtraArgs = ""){
  // update poms back to snapshot again
  sh 'mvn build-helper:parse-version versions:set -DnewVersion=\\\${parsedVersion.majorVersion}.\\\${parsedVersion.minorVersion}.\\\${parsedVersion.nextIncrementalVersion}-SNAPSHOT ' + mvnExtraArgs
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
  def oldVersion = getOldVersion()

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

def createPullRequest(String message, String project){
  def githubToken = getGitHubToken()
  sh "export GITHUB_TOKEN=${githubToken} && hub pull-request -m \"${message}\" > pr.txt"
  pr = readFile('pr.txt')
  split = pr.split('\\/')
  return split[6].trim()
}

def addMergeCommentToPullRequest(String pr, String project){
  def githubToken = getGitHubToken()
  def apiUrl = new URL("https://api.github.com/repos/${project}/issues/${pr}/comments")
  echo "merge PR using comment sent to ${apiUrl}"
  try {
    def HttpURLConnection connection = apiUrl.openConnection()
    if(githubToken.length() > 0)
    {
      connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
    }
    connection.setRequestMethod("POST")
    connection.setDoOutput(true)
    connection.connect()

    def body  = '{"body":"[merge]"}'

    OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())
    writer.write(body);
    writer.flush();

    // execute the POST request
    new InputStreamReader(connection.getInputStream())

    connection.disconnect()
  } catch (err) {
     echo "ERROR  ${err}"
     return
  }
}

def drop(String pr, String project){
  def githubToken = getGitHubToken()
  def apiUrl = new URL("https://api.github.com/repos/${project}/pulls/${pr}")
  def branch
  def HttpURLConnection connection
  def OutputStreamWriter writer
  echo "closing PR ${apiUrl}"

  try {
    connection = apiUrl.openConnection()
    if(githubToken.length() > 0)
    {
      connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
    }
    connection.setRequestMethod("POST")
    connection.setDoOutput(true)
    connection.connect()

    def body  = '''
    {
      "body": "release aborted",
      "state": "closed"
    }
    '''

    writer = new OutputStreamWriter(connection.getOutputStream())
    writer.write(body);
    writer.flush();

    // execute the POST request
    def rs = new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(),"UTF-8"))

    connection.disconnect()

    branchName = rs.head.ref

  } catch (err) {
     echo "ERROR  ${err}"
     return
  }

  try {
    apiUrl = new URL("https://api.github.com/repos/${project}/git/refs/heads/${branchName}")
    connection = apiUrl.openConnection()
    if(githubToken.length() > 0)
    {
      connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
    }
    connection.setRequestMethod("DELETE")
    connection.setDoOutput(true)
    connection.connect()

    writer = new OutputStreamWriter(connection.getOutputStream())
    writer.write(body);
    writer.flush();

    // execute the POST request
    new InputStreamReader(connection.getInputStream())

    connection.disconnect()

  } catch (err) {
     echo "ERROR  ${err}"
     return
  }
}

def deleteRemoteBranch(String branchName){
  sh "git push origin --delete ${branchName}"
}

def getGitHubToken(){
  def tokenPath = '/home/jenkins/.apitoken/hub'
  def githubToken = readFile tokenPath
  if (!githubToken?.trim()) {
    error "No GitHub token found in ${tokenPath}"
  }
  return githubToken.trim()
}

return this;
