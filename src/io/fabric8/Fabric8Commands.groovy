#!/usr/bin/groovy
package io.fabric8

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper
import io.fabric8.kubernetes.api.KubernetesHelper
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import jenkins.model.Jenkins

import java.util.regex.Pattern

def swizzleImageName(text, match, replace) {
    return Pattern.compile("image: ${match}:(.*)").matcher(text).replaceFirst("image: ${replace}")
}

def getReleaseVersionFromMavenMetadata(url) {
    def cmd = "curl -L ${url} | grep '<latest' | cut -f2 -d'>'|cut -f1 -d'<'"
    return sh(script: cmd, returnStdout: true).toString().trim()
}

def updatePackageJSONVersion(f, p, v) {
    sh "sed -i -r 's/\"${p}\": \"[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2})?(.[0-9][0-9]{0,2})?(-development)?\"/\"${p}\": \"${v}\"/g' ${f}"
}

def updateDockerfileEnvVar(f, p, v) {
    sh "sed -i -r 's/ENV ${p}.*/ENV ${p} ${v}/g' ${f}"
}

def getProjectVersion() {
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

    def modelMetaData = new XmlSlurper().parse(repo + '/' + artifact + '/maven-metadata.xml')
    def version = modelMetaData.versioning.release.text()
    return version
}

def isArtifactAvailableInRepo(String repo, String groupId, String artifactId, String version, String ext) {
    repo = removeTrailingSlash(repo)
    groupId = removeTrailingSlash(groupId)
    artifactId = removeTrailingSlash(artifactId)

    def url = new URL("${repo}/${groupId}/${artifactId}/${version}/${artifactId}-${version}.${ext}")
    HttpURLConnection connection = url.openConnection()

    connection.setRequestMethod("GET")
    connection.setDoInput(true)

    try {
        connection.connect()
        new InputStreamReader(connection.getInputStream(), "UTF-8")
        return true
    } catch (FileNotFoundException e1) {
        echo "File not yet available: ${url.toString()}"
        return false
    } finally {
        connection.disconnect()
    }
}

def isFileAvailableInRepo(String repo, String path, String version, String artifact) {
    repo = removeTrailingSlash(repo)
    path = removeTrailingSlash(path)
    version = removeTrailingSlash(version)

    def url = new URL("${repo}/${path}/${version}/${artifact}")

    HttpURLConnection connection = url.openConnection()

    connection.setRequestMethod("GET")
    connection.setDoInput(true)

    try {
        connection.connect()
        new InputStreamReader(connection.getInputStream(), "UTF-8")
        echo "File is available at: ${url.toString()}"
        return true
    } catch (FileNotFoundException e1) {
        echo "File not yet available: ${url.toString()}"
        return false
    } finally {
        connection.disconnect()
    }
}

def removeTrailingSlash(String myString) {
    if (myString.endsWith("/")) {
        return myString.substring(0, myString.length() - 1)
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
    def filelines = new String(repos).split('\n')
    for (int i = 0; i < filelines.size(); i++) {
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

def searchAndReplaceMavenVersionPropertyNoCommit(String property, String newVersion) {
    // example matches <fabric8.version>2.3</fabric8.version> <fabric8.version>2.3.12</fabric8.version> <fabric8.version>2.3.12.5</fabric8.version>
    sh "find -type f -name 'pom.xml' | xargs sed -i -r 's/${property}[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2})?(.[0-9][0-9]{0,2})?</${property}${newVersion}</g'"
}

def searchAndReplaceMavenVersionProperty(String property, String newVersion) {
    // example matches <fabric8.version>2.3</fabric8.version> <fabric8.version>2.3.12</fabric8.version> <fabric8.version>2.3.12.5</fabric8.version>
    sh "find -type f -name 'pom.xml' | xargs sed -i -r 's/${property}[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2})?(.[0-9][0-9]{0,2})?</${property}${newVersion}</g'"
    sh "git commit -a -m 'Bump ${property} version'"
}

def searchAndReplaceMavenSnapshotProfileVersionProperty(String property, String newVersion) {
    // example matches <fabric8.version>2.3-SNAPSHOT</fabric8.version> <fabric8.version>2.3.12-SNAPSHOT</fabric8.version> <fabric8.version>2.3.12.5-SNAPSHOT</fabric8.version>
    sh "find -type f -name 'pom.xml' | xargs sed -i -r 's/${property}[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2})?(.[0-9][0-9]{0,2})?-SNAPSHOT</${property}${newVersion}-SNAPSHOT</g'"
    sh "git commit -a -m 'Bump ${property} development profile SNAPSHOT version'"
}

def setupWorkspaceForRelease(String project, Boolean useGitTagForNextVersion, String mvnExtraArgs = "", String currentVersion = "") {
    sh "git config user.email fabric8-admin@googlegroups.com"
    sh "git config user.name fabric8-release"

    sh 'chmod 600 /root/.ssh-git/ssh-key'
    sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
    sh 'chmod 700 /root/.ssh-git'
    sh 'chmod 600 /home/jenkins/.gnupg/pubring.gpg'
    sh 'chmod 600 /home/jenkins/.gnupg/secring.gpg'
    sh 'chmod 600 /home/jenkins/.gnupg/trustdb.gpg'
    sh 'chmod 700 /home/jenkins/.gnupg'

    sh "git tag -d \$(git tag)"
    sh "git fetch --tags"

    if (useGitTagForNextVersion) {
        def newVersion = getNewVersionFromTag(currentVersion)
        echo "New release version ${newVersion}"
        sh "mvn -B -U versions:set -DnewVersion=${newVersion} " + mvnExtraArgs
        sh "git commit -a -m 'release ${newVersion}'"
        pushTag(newVersion)
    } else {
        sh 'mvn -B build-helper:parse-version versions:set -DnewVersion=\\\${parsedVersion.majorVersion}.\\\${parsedVersion.minorVersion}.\\\${parsedVersion.nextIncrementalVersion} ' + mvnExtraArgs
    }

    def releaseVersion = getProjectVersion()

    // delete any previous branches of this release
    try {
        sh "git checkout -b release-v${releaseVersion}"
    } catch (err) {
        sh "git branch -D release-v${releaseVersion}"
        sh "git checkout -b release-v${releaseVersion}"
    }
}

// if no previous tag found default 1.0.0 is used, else assume version is in the form major.minor or major.minor.micro version
def getNewVersionFromTag(pomVersion = null) {
    def version = '1.0.0'

    // Set known prerelease prefixes, needed for the proper sort order
    // in the next command
    sh "git config versionsort.prereleaseSuffix -RC"
    sh "git config versionsort.prereleaseSuffix -M"

    // if the repo has no tags this command will fail
    sh "git tag --sort version:refname | tail -1 > version.tmp"

    def tag = readFile 'version.tmp'

    if (tag == null || tag.size() == 0) {
        echo "no existing tag found using version ${version}"
        return version
    }

    tag = tag.trim()

    echo "Testing to see if version ${tag} is semver compatible"

    def semver = tag =~ /(?i)\bv?(?<major>0|[1-9]\d*)(?:\.(?<minor>0|[1-9]\d*)(?:\.(?<patch>0|[1-9]\d*))?)?(?:-(?<prerelease>[\da-z\-]+(?:\.[\da-z\-]+)*))?(?:\+(?<build>[\da-z\-]+(?:\.[\da-z\-]+)*))?\b/

    if (semver.matches()) {
        echo "Version ${tag} is semver compatible"

        def majorVersion = semver.group('major') as int
        def minorVersion = (semver.group('minor') ?: 0) as int
        def patchVersion = ((semver.group('patch') ?: 0) as int) + 1

        echo "Testing to see if current POM version ${pomVersion} is semver compatible"

        def pomSemver = pomVersion.trim() =~ /(?i)\bv?(?<major>0|[1-9]\d*)(?:\.(?<minor>0|[1-9]\d*)(?:\.(?<patch>0|[1-9]\d*))?)?(?:-(?<prerelease>[\da-z\-]+(?:\.[\da-z\-]+)*))?(?:\+(?<build>[\da-z\-]+(?:\.[\da-z\-]+)*))?\b/
        if (pomSemver.matches()) {
            echo "Current POM version ${pomVersion} is semver compatible"

            def pomMajorVersion = pomSemver.group('major') as int
            def pomMinorVersion = (pomSemver.group('minor') ?: 0) as int
            def pomPatchVersion = (pomSemver.group('patch') ?: 0) as int

            if (pomMajorVersion > majorVersion ||
                    (pomMajorVersion == majorVersion &&
                            (pomMinorVersion > minorVersion) || (pomMinorVersion == minorVersion && pomPatchVersion > patchVersion)
                    )
            ) {
                majorVersion = pomMajorVersion
                minorVersion = pomMinorVersion
                patchVersion = pomPatchVersion
            }
        }

        def newVersion = "${majorVersion}.${minorVersion}.${patchVersion}"
        echo "New version is ${newVersion}"
        return newVersion
    } else {
        echo "Version is not semver compatible"

        // strip the v prefix from the tag so we can use in a maven version number
        def previousReleaseVersion = tag.substring(tag.lastIndexOf('v') + 1)
        echo "Previous version found ${previousReleaseVersion}"

        // if there's an int as the version then turn it into a major.minor.micro version
        if (previousReleaseVersion.isNumber()) {
            return previousReleaseVersion + '.0.1'
        } else {
            // if previous tag is not a number and doesnt have a '.' version seperator then error until we have one
            if (previousReleaseVersion.lastIndexOf('.') == 0) {
                error "found invalid latest tag [${previousReleaseVersion}] set to major.minor.micro to calculate next release version"
            }
            // increment the release number after the last seperator '.'
            def microVersion = previousReleaseVersion.substring(previousReleaseVersion.lastIndexOf('.') + 1) as int
            return previousReleaseVersion.substring(0, previousReleaseVersion.lastIndexOf('.') + 1) + (microVersion + 1)
        }
    }
}

def stageSonartypeRepo() {
    try {
        sh "mvn clean -B"
        sh "mvn -V -B -e -U install org.sonatype.plugins:nexus-staging-maven-plugin:1.6.7:deploy -P release -P openshift -DnexusUrl=https://oss.sonatype.org -DserverId=oss-sonatype-staging -Ddocker.push.registry=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}"

        // lets not archive artifacts until we if we just use nexus or a content repo
        //step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])

    } catch (err) {
        hubotSend room: 'release', message: "Release failed when building and deploying to Nexus ${err}", failOnError: false
        currentBuild.result = 'FAILURE'
        error "ERROR Release failed when building and deploying to Nexus ${err}"
    }
    // the sonartype staging repo id gets written to a file in the workspace
    return getRepoIds()
}

def releaseSonartypeRepo(String repoId) {
    try {
        // release the sonartype staging repo
        sh "mvn -B org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"

    } catch (err) {
        sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-drop -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Error during release: ${err}\" -DstagingProgressTimeoutMinutes=60"
        currentBuild.result = 'FAILURE'
        error "ERROR releasing sonartype repo ${repoId}: ${err}"
    }
}

def dropStagingRepo(String repoId) {
    echo "Not a release so dropping staging repo ${repoId}"
    sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-drop -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Dry run\" -DstagingProgressTimeoutMinutes=60"
}

def helm() {
    def pluginVersion = getReleaseVersion("io/fabric8/fabric8-maven-plugin")
    try {
        sh "mvn -B io.fabric8:fabric8-maven-plugin:${pluginVersion}:helm"
        sh "mvn -B io.fabric8:fabric8-maven-plugin:${pluginVersion}:helm-push"
    } catch (err) {
        error "ERROR with helm push ${err}"
    }
}

def pushTag(String releaseVersion) {
    sh "git tag -fa v${releaseVersion} -m 'Release version ${releaseVersion}'"
    sh "git push origin v${releaseVersion}"
}


def updateGithub() {
    def releaseVersion = getProjectVersion()
    sh "git push origin release-v${releaseVersion}"
}

def updateNextDevelopmentVersion(String releaseVersion, String mvnExtraArgs = "") {
    // update poms back to snapshot again
    sh 'mvn -B build-helper:parse-version versions:set -DnewVersion=\\\${parsedVersion.majorVersion}.\\\${parsedVersion.minorVersion}.\\\${parsedVersion.nextIncrementalVersion}-SNAPSHOT ' + mvnExtraArgs
    def snapshotVersion = getProjectVersion()
    sh "git commit -a -m '[CD] prepare for next development iteration ${snapshotVersion}'"
    sh "git push origin release-v${releaseVersion}"
}

def hasChangedSinceLastRelease() {
    sh "git log --name-status HEAD^..HEAD -1 --grep=\"prepare for next development iteration\" --author='fusesource-ci' >> gitlog.tmp"
    def myfile = readFile('gitlog.tmp')
    //sh "rm gitlog.tmp"
    // if the file size is 0 it means the CI user was not the last commit so project has changed
    if (myfile.length() == 0) {
        return true
    } else {
        return false
    }
}

def getOldVersion() {
    def matcher = readFile('website/src/docs/index.page') =~ '<h1>Documentation for version (.+)</h1>'
    matcher ? matcher[0][1] : null
}

def updateDocsAndSite(String newVersion) {
    // get previous version
    def oldVersion = getOldVersion()

    if (oldVersion == null) {
        echo "No previous version found"
        return
    }

    // use perl so that we we can easily turn off regex in the SED query as using dots in version numbers returns unwanted results otherwise
    sh "find . -name '*.md' ! -name Changes.md ! -path '*/docs/jube/**.*' | xargs perl -p -i -e 's/\\Q${oldVersion}/${newVersion}/g'"
    sh "find . -path '*/website/src/**.*' | xargs perl -p -i -e 's/\\Q${oldVersion}/${newVersion}/g'"

    sh "git commit -a -m '[CD] Update docs following ${newVersion} release'"

}

def runSystemTests() {
    sh 'cd systests && mvn clean && mvn integration-test verify'
}

def createPullRequest(String message, String project, String branch) {
    def githubToken = getGitHubToken()
    def apiUrl = new URL("https://api.github.com/repos/${project}/pulls")
    echo "creating PR for ${apiUrl}"
    try {
        HttpURLConnection connection = apiUrl.openConnection()
        if (githubToken.length() > 0) {
            connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
        }
        connection.setRequestMethod("POST")
        connection.setDoOutput(true)
        connection.connect()

        def body = """
    {
      "title": "${message}",
      "head": "${branch}",
      "base": "master"
    }
    """
        echo "sending body: ${body}\n"

        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())
        writer.write(body)
        writer.flush()

        // execute the POST request
        def rs = new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(), "UTF-8"))

        connection.disconnect()

        echo "Received PR id:  ${rs.number}"
        return rs.number + ''

    } catch (err) {
        error "ERROR  ${err}"
    }
}

def closePR(project, id, newVersion, newPRID) {
    def githubToken = getGitHubToken()
    def apiUrl = new URL("https://api.github.com/repos/${project}/pulls/${id}")
    echo "deleting PR for ${apiUrl}"

    HttpURLConnection connection = apiUrl.openConnection()
    if (githubToken.length() > 0) {
        connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
    }
    connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
    connection.setRequestMethod("POST")
    connection.setDoOutput(true)
    connection.connect()

    def body = """
    {
      "state": "closed",
      "body": "Superseded by new version ${newVersion} #${newPRID}"
    }
    """
    echo "sending body: ${body}\n"

    OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())
    writer.write(body)
    writer.flush()

    // execute the PATCH     request
    def rs = new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(), "UTF-8"))

    def code = connection.getResponseCode()

    if (code != 200) {
        error "${project} PR ${id} not merged.  ${connection.getResponseMessage()}"

    } else {
        echo "${project} PR ${id} ${rs.message}"
    }
    connection.disconnect()
}

def getIssueComments(project, id, githubToken = null) {
    if (!githubToken){
        githubToken = getGitHubToken()
    }
    def apiUrl = new URL("https://api.github.com/repos/${project}/issues/${id}/comments")
    echo "getting comments for ${apiUrl}"

    def HttpURLConnection connection = apiUrl.openConnection()
    if (githubToken != null && githubToken.length() > 0) {
        connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
    }

    connection.setRequestMethod("GET")
    connection.setDoOutput(true)
    connection.connect()

    def rs = new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(), "UTF-8"))

    def code = 0
    try {
        code = connection.getResponseCode()
    // } catch (org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException ex){
    //     echo "${ex} will try to continue"
    } finally {
        connection.disconnect()
    }

    if (code != 0 && code != 200) {
        error "Cannot get ${project} PR ${id} comments.  ${connection.getResponseMessage()}"
    }

    return rs
}

def waitUntilSuccessStatus(project, ref) {

    def githubToken = getGitHubToken()

    def apiUrl = new URL("https://api.github.com/repos/${project}/commits/${ref}/status")
    waitUntil {
        def HttpURLConnection connection = apiUrl.openConnection()
        if (githubToken != null && githubToken.length() > 0) {
            connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
        }

        connection.setRequestMethod("GET")
        connection.setDoOutput(true)
        connection.connect()

        def rs
        def code

        try {
            rs = new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(), "UTF-8"))

            code = connection.getResponseCode()
        } catch (err){
            echo "CI checks have not passed yet so waiting before merging"
        } finally {
            connection.disconnect()
        }

        if (rs == null){
            echo "Error getting commit status, are CI builds enabled for this PR?"
            return false
        }
        if (rs != null && rs.state == 'success') {
            return true
        } else {
            echo "Commit status is ${rs.state}.  Waiting to merge"
            return false
        }
    }
}

def getGithubBranch(project, id, githubToken){

    def apiUrl = new URL("https://api.github.com/repos/${project}/pulls/${id}")
    def HttpURLConnection connection = apiUrl.openConnection()
    if (githubToken != null && githubToken.length() > 0) {
        connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
    }

    connection.setRequestMethod("GET")
    connection.setDoOutput(true)
    connection.connect()
    try{
        def rs = new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(), "UTF-8"))
        branch = rs.head.ref
        echo "${branch}"
        return branch
    }catch(err){
        echo "Error while fetching the github branch"
    }finally {
        if (connection){
            connection.disconnect()
        }
    }
}

def mergePR(project, id) {
    def githubToken = getGitHubToken()
    def branch = getGithubBranch(project, id, githubToken)
    waitUntilSuccessStatus(project, branch)

    def apiUrl = new URL("https://api.github.com/repos/${project}/pulls/${id}/merge")

    def HttpURLConnection connection = apiUrl.openConnection()
    if (githubToken.length() > 0) {
        connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
    }
    connection.setRequestMethod("PUT")
    connection.setDoOutput(true)
    connection.connect()

    // execute the request
    def rs
    try{
        rs = new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(), "UTF-8"))

        def code = connection.getResponseCode()

        if (code != 200) {
            if (code == 405) {
                error "${project} PR ${id} not merged.  ${rs.message}"
            } else {
                error "${project} PR ${id} not merged.  GitHub API Response code: ${code}"
            }
        } else {
            echo "${project} PR ${id} ${rs.message}"
        }
    } catch (err) {
        // if merge failed try to squash and merge
        connection = null
        rs = null
        squashAndMerge(project, id)
    } finally {
        if (connection){
            connection.disconnect()
            connection = null
        }
        rs = null
    }
}

def squashAndMerge(project, id) {
    def githubToken = getGitHubToken()
    def apiUrl = new URL("https://api.github.com/repos/${project}/pulls/${id}/merge")

    def HttpURLConnection connection = apiUrl.openConnection()
    if (githubToken.length() > 0) {
        connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
    }
    connection.setRequestMethod("PUT")
    connection.setDoOutput(true)
    connection.connect()
    def body = "{\"merge_method\":\"squash\"}"

    def rs
    try{
        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())
        writer.write(body)
        writer.flush()

        rs = new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(), "UTF-8"))
        def code = connection.getResponseCode()

        if (code != 200) {
            if (code == 405) {
                error "${project} PR ${id} not merged.  ${rs.message}"
            } else {
                error "${project} PR ${id} not merged.  GitHub API Response code: ${code}"
            }
        } else {
            echo "${project} PR ${id} ${rs.message}"
        }
    } finally {
        connection.disconnect()
        connection = null
        rs = null
    }
}

def addCommentToPullRequest(comment, pr, project) {
    def githubToken = getGitHubToken()
    def apiUrl = new URL("https://api.github.com/repos/${project}/issues/${pr}/comments")
    echo "adding ${comment} to ${apiUrl}"
    try {
        def HttpURLConnection connection = apiUrl.openConnection()
        if (githubToken.length() > 0) {
            connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
        }
        connection.setRequestMethod("POST")
        connection.setDoOutput(true)
        connection.connect()

        def body = "{\"body\":\"${comment}\"}"

        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())
        writer.write(body)
        writer.flush()

        // execute the POST request
        new InputStreamReader(connection.getInputStream())

        connection.disconnect()
    } catch (err) {
        error "ERROR  ${err}"
    }
}

def addMergeCommentToPullRequest(String pr, String project) {
    def githubToken = getGitHubToken()
    def apiUrl = new URL("https://api.github.com/repos/${project}/issues/${pr}/comments")
    echo "merge PR using comment sent to ${apiUrl}"
    try {
        def HttpURLConnection connection = apiUrl.openConnection()
        if (githubToken.length() > 0) {
            connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
        }
        connection.setRequestMethod("POST")
        connection.setDoOutput(true)
        connection.connect()

        def body = '{"body":"[merge]"}'

        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())
        writer.write(body)
        writer.flush()

        // execute the POST request
        new InputStreamReader(connection.getInputStream())

        connection.disconnect()
    } catch (err) {
        error "ERROR  ${err}"
    }
}

def getGitHubProject(){
    def url = getScmPushUrl()
    if (!url.contains('github.com')){
        error "${url} is not a GitHub URL"
    }

    if (url.contains("https://github.com/")){
        url = url.replaceAll("https://github.com/", '')

    } else if (url.contains("git@github.com:")){
        url = url.replaceAll("git@github.com:", '')
    }

    if (url.contains(".git")){
        url = url.replaceAll(".git", '')
    }
    return url.trim()
}

def isAuthorCollaborator(githubToken, project) {

    if (!githubToken){

        githubToken = getGitHubToken()

        if (!githubToken){
            echo "No GitHub api key found so trying annonynous GitHub api call"
        }
    }
    if (!project){
        project = getGitHubProject()
    }

    def changeAuthor = env.CHANGE_AUTHOR
    if (!changeAuthor){
        error "No commit author found.  Is this a pull request pipeline?"
    }
    echo "Checking if user ${changeAuthor} is a collaborator on ${project}"

    def apiUrl = new URL("https://api.github.com/repos/${project}/collaborators/${changeAuthor}")

    def HttpURLConnection connection = apiUrl.openConnection()
    if (githubToken != null && githubToken.length() > 0) {
        connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
    }
    connection.setRequestMethod("GET")
    connection.setDoOutput(true)

    try {
        connection.connect()
        new InputStreamReader(connection.getInputStream(), "UTF-8")
        return true
    } catch (FileNotFoundException e1) {
        return false
    } finally {
        connection.disconnect()
    }

    error "Error checking if user ${changeAuthor} is a collaborator on ${project}.  GitHub API Response code: ${code}"

}

def getUrlAsString(urlString) {

    def url = new URL(urlString)
    def scan
    def response
    echo "getting string from URL: ${url}"
    try {
        scan = new Scanner(url.openStream(), "UTF-8")
        response = scan.useDelimiter("\\A").next()
    } finally {
        scan.close()
    }
    return response
}


def drop(String pr, String project) {
    def githubToken = getGitHubToken()
    def apiUrl = new URL("https://api.github.com/repos/${project}/pulls/${pr}")
    def branch
    HttpURLConnection connection
    OutputStreamWriter writer
    echo "closing PR ${apiUrl}"

    try {
        connection = apiUrl.openConnection()
        if (githubToken.length() > 0) {
            connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
        }
        connection.setRequestMethod("POST")
        connection.setDoOutput(true)
        connection.connect()

        def body = '''
    {
      "body": "release aborted",
      "state": "closed"
    }
    '''

        writer = new OutputStreamWriter(connection.getOutputStream())
        writer.write(body)
        writer.flush()

        // execute the POST request
        def rs = new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(), "UTF-8"))

        connection.disconnect()

        branchName = rs.head.ref

    } catch (err) {
        error "ERROR  ${err}"
    }

    try {
        apiUrl = new URL("https://api.github.com/repos/${project}/git/refs/heads/${branchName}")
        connection = apiUrl.openConnection()
        if (githubToken.length() > 0) {
            connection.setRequestProperty("Authorization", "Bearer ${githubToken}")
        }
        connection.setRequestMethod("DELETE")
        connection.setDoOutput(true)
        connection.connect()

        writer = new OutputStreamWriter(connection.getOutputStream())
        writer.write(body)
        writer.flush()

        // execute the POST request
        new InputStreamReader(connection.getInputStream())

        connection.disconnect()

    } catch (err) {
        error "ERROR  ${err}"
    }
}

def deleteRemoteBranch(String branchName, containerName) {
    container(name: containerName) {
        sh 'chmod 600 /root/.ssh-git/ssh-key'
        sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
        sh 'chmod 700 /root/.ssh-git'
        sh "git push origin --delete ${branchName}"
    }
}

 def getGitHubToken() {
    def tokenPath = '/home/jenkins/.apitoken/hub'
    def githubToken = readFile tokenPath
    if (!githubToken?.trim()) {
        error "No GitHub token found in ${tokenPath}"
    }
    return githubToken.trim()
}

@NonCPS
def isSingleNode() {
    KubernetesClient kubernetes = new DefaultKubernetesClient()
    if (kubernetes.nodes().list().getItems().size() == 1) {
        return true
    } else {
        return false
    }
}

@NonCPS
def hasService(String name) {
    KubernetesClient kubernetes = new DefaultKubernetesClient()

    def service = kubernetes.services().withName(name).get()
    if (service != null) {
        return service.metadata != null
    }
    return false
}

@NonCPS
def getServiceURL(String serviceName, String namespace = null, String protocol = "http", boolean external = true) {
    KubernetesClient kubernetes = new DefaultKubernetesClient()
    if (namespace == null) namespace = kubernetes.getNamespace()
    return KubernetesHelper.getServiceURL(kubernetes, serviceName, namespace, protocol, external)
}

def hasOpenShiftYaml() {
  def openshiftYaml = findFiles(glob: '**/openshift.yml')
    try {
        if (openshiftYaml) {
            def contents = readFile(openshiftYaml[0].path)
            if (contents != null) {
                if (contents.contains('kind: "ImageStream"') || contents.contains('kind: ImageStream') || contents.contains('kind: \'ImageStream\'')) {
                    echo "OpenShift YAML contains an ImageStream"
                    return true
                } else {
                    echo "OpenShift YAML does not contain an ImageStream so not using S2I binary mode"
                }
            }
        } else {
            echo "Warning OpenShift YAML ${openshiftYaml} does not exist!"
        }
    } catch (e) {
        error "Failed to load ${openshiftYaml[0]} due to ${e}"
    }
    return false
}

/**
 * Deletes the given namespace if it exists
 *
 * @param name the name of the namespace
 * @return true if the delete was successful
 */
@NonCPS
def deleteNamespace(String name) {
    KubernetesClient kubernetes = new DefaultKubernetesClient()
    try {
        def namespace = kubernetes.namespaces().withName(name).get()
        if (namespace != null) {
            echo "Deleting namespace ${name}..."
            kubernetes.namespaces().withName(name).delete()
            echo "Deleted namespace ${name}"

            // TODO should we wait for the namespace to really go away???
            namespace = kubernetes.namespaces().withName(name).get()
            if (namespace != null) {
                echo "Namespace ${name} still exists!"
            }
            return true
        }
        return false
    } catch (e) {
        // ignore errors
        return false
    }
}

@NonCPS
def isOpenShift() {
    return new DefaultOpenShiftClient().isAdaptable(OpenShiftClient.class)
}

@NonCPS
def getCloudConfig() {
    def openshiftCloudConfig = Jenkins.getInstance().getCloud('openshift')
    return (openshiftCloudConfig) ? 'openshift' : 'kubernetes'
}

/**
 * Should be called after checkout scm
 */
@NonCPS
def getScmPushUrl() {
    def url = sh(returnStdout: true, script: 'git config --get remote.origin.url').trim()

    if (!url){
        error "no URL found for git config --get remote.origin.url "
    }
    return url
}

@NonCPS
def openShiftImageStreamExists(String name){
    if (isOpenShift()) {
        try {
            def result = sh(returnStdout: true, script: 'oc describe is ${name} --namespace openshift')
            if (result && result.contains(name)){
                echo "ImageStream  ${name} is already installed globally"
                return true;
            }else {
                //see if its already in our namespace
                def namespace = kubernetes.getNamespace();
                result = sh(returnStdout: true, script: 'oc describe is ${name} --namespace ${namespace}')
                if (result && result.contains(name)){
                    echo "ImageStream  ${name} is already installed in project ${namespace}"
                    return true;
                }
            }
        }catch (e){
            echo "Warning: ${e} "
        }
    }
    return false;
}

@NonCPS
def openShiftImageStreamInstall(String name, String location){
    if (openShiftImageStreamExists(name)) {
        echo "ImageStream ${name} does not exist - installing ..."
        try {
            def result = sh(returnStdout: true, script: 'oc create -f  ${location}')
            def namespace = kubernetes.getNamespace();
            echo "ImageStream ${name} now installed in project ${namespace}"
            return true;
        }catch (e){
            echo "Warning: ${e} "
        }
    }
    return false;
}

return this
