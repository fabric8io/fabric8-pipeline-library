#!/usr/bin/groovy
package io.fabric8

import com.cloudbees.groovy.cps.NonCPS
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import io.fabric8.Fabric8Commands
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import io.fabric8.jenkins.openshiftsync.BuildCause
import io.fabric8.kubernetes.api.environments.Environments

@NonCPS
def environmentNamespace(environment) {
  KubernetesClient kubernetes = new DefaultKubernetesClient()
  def ns = getNamespace()
  if (ns.endsWith("-jenkins")){
    ns = ns.substring(0, ns.lastIndexOf("-jenkins"))
  }

  return ns + "-${environment}"
}

@NonCPS
def getNamespace() {
  KubernetesClient client = new DefaultKubernetesClient()
  return client.getNamespace()
}

@NonCPS
def getImageStreamSha(imageStreamName) {
  OpenShiftClient oc = new DefaultOpenShiftClient()
  return findTagSha(oc, imageStreamName, getNamespace())
}

// returns the tag sha from an imagestream
// original code came from the fabric8-maven-plugin
@NonCPS
def findTagSha(OpenShiftClient client, String imageStreamName, String namespace) {
  def currentImageStream = null
  for (int i = 0; i < 15; i++) {
    if (i > 0) {
      echo("Retrying to find tag on ImageStream ${imageStreamName}")
      try {
        Thread.sleep(1000)
      } catch (InterruptedException e) {
        echo("interrupted ${e}")
      }
    }
    currentImageStream = client.imageStreams().withName(imageStreamName).get()
    if (currentImageStream == null) {
      continue
    }
    def status = currentImageStream.getStatus()
    if (status == null) {
      continue
    }
    def tags = status.getTags()
    if (tags == null || tags.isEmpty()) {
      continue
    }
    // latest tag is the first
    TAG_EVENT_LIST:
    for (def list : tags) {
      def items = list.getItems()
      if (items == null) {
        continue TAG_EVENT_LIST
      }
      // latest item is the first
      for (def item : items) {
        def image = item.getImage()
        if (image != null && image != '') {
          echo("Found tag on ImageStream " + imageStreamName + " tag: " + image)
          return image
        }
      }
    }
  }
  // No image found, even after several retries:
  if (currentImageStream == null) {
    error ("Could not find a current ImageStream with name " + imageStreamName + " in namespace " + namespace)
  } else {
    error ("Could not find a tag in the ImageStream " + imageStreamName)
  }
}

@NonCPS
def addAnnotationToBuild(annotation, value) {
  def flow = new Fabric8Commands()
  if (flow.isOpenShift()) {
    def buildName = getValidOpenShiftBuildName()
    echo "Adding annotation '${annotation}: ${value}' to Build ${buildName}"
    OpenShiftClient oClient = new DefaultOpenShiftClient()
    def usersNamespace = getUsersNamespace()
    echo "looking for ${buildName} in namespace ${usersNamespace}"
    oClient.builds().inNamespace(usersNamespace).withName(buildName).edit().editMetadata().addToAnnotations(annotation, value).endMetadata().done()
  } else {
    echo "Not running on openshift so skip adding annotation ${annotation}: value"
  }
}

@NonCPS
def getUsersNamespace(){
    def usersNamespace = getNamespace()
    if (usersNamespace.endsWith("-jenkins")){
      usersNamespace = usersNamespace.substring(0, usersNamespace.lastIndexOf("-jenkins"))
    }
    return usersNamespace
}


def isCI(){

  // if we are running a branch plugin generated job check the env var
  if (env.BRANCH_NAME){
    if (env.BRANCH_NAME.startsWith('PR-')) {
      addPipelineAnnotationToBuild('ci')
      return true
    }
    return false
  }

  // otherwise if we aren't running on master then this is a CI build
  def branch = sh(script: 'git symbolic-ref --short HEAD', returnStdout: true).toString().trim()

  if (branch.equals('master')) {
    return false
  }
  addPipelineAnnotationToBuild('ci')
  return true
}

def isCD(){

  // if we are running a branch plugin generated job check the env var
  if (env.BRANCH_NAME){
    if (env.BRANCH_NAME.equals('master')) {
      addPipelineAnnotationToBuild('cd')
      return true
    }
    return false
  }

  // otherwise if we are running on master then this is a CD build
  def branch = sh(script: 'git symbolic-ref --short HEAD', returnStdout: true).toString().trim()

  if (branch.equals('master')) {
    addPipelineAnnotationToBuild('cd')
    return true
  }
  return false
}

def addPipelineAnnotationToBuild(t){
    addAnnotationToBuild('fabric8.io/pipeline.type', t)
}

def getLatestVersionFromTag(){
  sh 'git fetch --tags'
  sh 'git config versionsort.prereleaseSuffix -RC'
  sh 'git config versionsort.prereleaseSuffix -M'

  // if the repo has no tags this command will fail
  def version = sh(script: 'git tag --sort version:refname | tail -1', returnStdout: true).toString().trim()

  if (version == null || version.size() == 0){
    error 'no release tag found'
  }
  return version.startsWith("v") ? version.substring(1) : version
}

def getBranch(){
  if (env.BRANCH_NAME){
    return env.BRANCH_NAME
  } else {
    return sh(script: 'git symbolic-ref --short HEAD', returnStdout: true).toString().trim()
  }
}

@NonCPS
def isValidBuildName(buildName){
  def flow = new Fabric8Commands()
  if (flow.isOpenShift()) {
    echo "Looking for matching Build ${buildName}"
    OpenShiftClient oClient = new DefaultOpenShiftClient()
    def usersNamespace = getUsersNamespace()
    def build = oClient.builds().inNamespace(usersNamespace).withName(buildName).get()
    if (build){
      return true
    }
    return false
  } else {
    error "Not running on openshift so cannot lookup build names"
  }
}

@NonCPS
def getValidOpenShiftBuildName(){

  def buildName = getOpenShiftBuildName()
  if (isValidBuildName(buildName)){
    return buildName
  } else {
    error "No matching openshift build with name ${buildName} found"
  }
}

def replacePackageVersion(packageLocation, pair){

  def property = pair[0]
  def version = pair[1]

  sh "sed -i -r 's/\"${property}\": \"[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2})?(.[0-9][0-9]{0,2})?(-development)?\"/\"${property}\": \"${version}\"/g' ${packageLocation}"

}

def replacePackageVersions(packageLocation, replaceVersions){
  for(int i = 0; i < replaceVersions.size(); i++){
    replacePackageVersion(packageLocation, replaceVersions[i])
  }
}


def getExistingPR(project, pair){
    def property = pair[0]
    def version = pair[1]

    def flow = new Fabric8Commands()
    def githubToken = flow.getGitHubToken()
    def apiUrl = new URL("https://api.github.com/repos/${project}/pulls")
    def rs = restGetURL{
        authString = githubToken
        url = apiUrl
    }

    if (rs == null || rs.isEmpty()){
      return false
    }
    for(int i = 0; i < rs.size(); i++){
      def pr = rs[i]

      if (pr.state == 'open' && pr.title.contains("fix(version): update ${property}")){
        if (!pr.title.contains("fix(version): update ${property} to ${version}")){
          return pr.number
        }
      }
    }
    return null
}

def getDownstreamProjectOverrides(project, id, downstreamProject, botName = '@fabric8cd'){

  if (!downstreamProject){
    error 'no downstreamProjects provided'
  }
  def flow = new Fabric8Commands()
  def comments = flow.getIssueComments(project, id)
  // start by looking at the most recent commments and work back
  Collections.reverse(comments)
  for (comment in comments) {
    echo "Found PR comment ${comment.body}"
    def text = comment.body.trim()
    def match = 'CI downstream projects'
    if (text.startsWith(botName)){
      if (text.contains(match)){
        def result = text.substring(text.indexOf("[") + 1, text.indexOf("]"))
        if (!result){
          echo 'no downstream projects found'
        }
        def list =  result.split(',')
        for (repos in list) {
          if (!repos.contains('=')){
            error 'no override project found in the form organisation=foo'
          }
          def overrides =  repos.split('=')
          if (downstreamProject == overrides[0].trim()){
            "matched and returning ${overrides[1].trim()}"
            return overrides[1].trim()
          }
        }
      }
    }
  }
}

def getDownstreamProjectOverrides(downstreamProject, botName = '@fabric8cd'){

  def flow = new Fabric8Commands()

  def id = env.CHANGE_ID
  if (!id){
    error 'no env.CHANGE_ID / pull request id found'
  }

  def project = getRepoName()

  return getDownstreamProjectOverrides(project, id, downstreamProject, botName = '@fabric8cd')
}


def isSkipCIDeploy(botName = '@fabric8cd'){
  def id = env.CHANGE_ID
  if (!id){
    error 'no env.CHANGE_ID / pull request id found'
  }

  def flow = new Fabric8Commands()
  def project = getRepoName()

  def comments = flow.getIssueComments(project, id)
  // start by looking at the most recent commments and work back
  Collections.reverse(comments)
  for (comment in comments) {
    echo comment.body
    def text = comment.body.trim()
    def skipTrue = 'CI skip deploy=true'
    def skipFalse = 'CI skip deploy=false'
    if (text.startsWith(botName)){
      if (text.contains(skipTrue)){
        return true
      } else if (text.contains(skipFalse)){
        return false
      }
    }
  }
}

// helper to get the repo name from the job name when using org + branch github plugins
def getRepoName(){

  def jobName = env.JOB_NAME

  // job name from the org plugin
  if (jobName.count('/') > 1){
    return jobName.substring(jobName.indexOf('/')+1, jobName.lastIndexOf('/'))
  }
  // job name from the branch plugin
  if (jobName.count('/') > 0){
    return jobName.substring(0, jobName.lastIndexOf('/'))
  }
  // normal job name
  return jobName
}

@NonCPS
def getOpenShiftBuildName(){

  def activeInstance = Jenkins.getActiveInstance()
  def  job = (WorkflowJob) activeInstance.getItemByFullName(env.JOB_NAME)
  def run = job.getBuildByNumber(Integer.parseInt(env.BUILD_NUMBER))
  def cause = (BuildCause) run.getCause(BuildCause.class)
  return cause.getName()
}


return this
