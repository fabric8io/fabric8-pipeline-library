def isRelease = ""
try {
  isRelease = IS_RELEASEBUILD
} catch (Throwable e) {
  isRelease = "${env.IS_RELEASEBUILD ?: 'false'}"
}
def releaseVersion = ""
try {
  releaseVersion = RELEASE_VERSION
} catch (Throwable e) {
  releaseVersion = "${env.RELEASE_VERSION}"
}
def nextSnapshotVersion = ""
try {
  nextSnapshotVersion = NEXT_SNAPSHOT_VERSION
} catch (Throwable e) {
  nextSnapshotVersion = "${env.NEXT_SNAPSHOT_VERSION ?: '2.3-SNAPSHOT'}"
}
def updateFabric8ReleaseDeps = ""
try {
  updateFabric8ReleaseDeps = UPDATE_FABRIC8_RELEASE_DEPENDENCIES
} catch (Throwable e) {
  updateFabric8ReleaseDeps = "${env.UPDATE_FABRIC8_RELEASE_DEPENDENCIES ?: 'false'}"
}

def getReleaseVersion(String project) {
  def modelMetaData = new XmlSlurper().parse("https://oss.sonatype.org/content/repositories/releases/io/fabric8/"+project+"/maven-metadata.xml")
  def version = modelMetaData.versioning.release.text()
  return version
}

stage 'canary release fabric8-ipaas'
node {
  ws ('fabric8-ipaas'){
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
      def project = "fabric8io/fabric8-ipaas"

      sh "rm -rf *.*"
      git "https://github.com/${project}"
      sh "git remote set-url origin git@github.com:${project}.git"

      sh "git config user.email fabric8-admin@googlegroups.com"
      sh "git config user.name fusesource-ci"

      sh "git checkout master"

      sh "git tag -d \$(git tag)"
      sh "git fetch --tags"
      sh "git reset --hard origin/master"

      // bump dependency versions from the previous stage
      if(updateFabric8ReleaseDeps == 'true'){
        try {
          def fabric8Version = getReleaseVersion("fabric8-maven-plugin")
          sh "find -type f -name 'pom.xml' | xargs sed -i -r 's/<fabric8.version>[0-9][0-9]{0,2}.[0-9][0-9]{0,2}.[0-9][0-9]{0,2}/<fabric8.version>${fabric8Version}/g'"
          sh "git commit -a -m \"Bump fabric8 version\""
        } catch (err) {
          echo "Already on the latest versions of fabric8 dependencies"
        }
      }

      // lets avoid using the maven release plugin so we have more control over the release
      sh "mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -DnewVersion=${releaseVersion}"
      sh "mvn -V -B -U clean install org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:deploy -P release -DnexusUrl=https://oss.sonatype.org -DserverId=oss-sonatype-staging"

      // get the repo id and store it in a file see https://issues.jenkins-ci.org/browse/JENKINS-26133
      sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-list -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org | grep OPEN | grep -Eo 'iofabric8-[[:digit:]]+' > repoId.txt"
      def repoId = readFile('repoId.txt').trim()

      if(isRelease == 'true'){
        try {

          // intermittent errors can occur when pushing to dockerhub
          retry(3){
            sh "mvn docker:push -P release -Ddocker.username=${env.DOCKER_REGISTRY_USERNAME} -Ddocker.password=${env.DOCKER_REGISTRY_PASSWORD}"
          }

          // close and release the sonartype staging repo
          sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-close -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
          sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"

        } catch (err) {
          sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-drop -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Error during release: ${err}\" -DstagingProgressTimeoutMinutes=60"
          currentBuild.result = 'FAILURE'
          return
        }

        // push release versions and tag it
        sh "git commit -a -m \"[CD] prepare release v${releaseVersion}\""
        sh "git push origin master"
        sh "git tag -a v${releaseVersion} -m 'Release version ${releaseVersion}'"
        sh "git push origin v${releaseVersion}"

        // update poms back to snapshot again
        sh "mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -DnewVersion=${nextSnapshotVersion}"
        sh "git commit -a -m \"[CD] prepare for next development iteration\""
        sh "git push origin master"

      } else {
        echo "Not a real release so closing sonartype repo"
        sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-drop -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Relase not needed\" -DstagingProgressTimeoutMinutes=60"
      }
    }
  }
}
