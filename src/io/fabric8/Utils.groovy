#!/usr/bin/groovy
package io.fabric8

import com.cloudbees.groovy.cps.NonCPS
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.openshift.api.model.Build
import io.fabric8.openshift.api.model.ImageStream
import io.fabric8.openshift.api.model.ImageStreamStatus
import io.fabric8.openshift.api.model.NamedTagEventList
import io.fabric8.openshift.api.model.TagEvent
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import io.fabric8.Fabric8Commands

//
//IMAGE_STREAM_TAG_RETRIES = 15;
//IMAGE_STREAM_TAG_RETRY_TIMEOUT_IN_MILLIS = 1000;

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
  KubernetesClient kubernetes = new DefaultKubernetesClient()
  return kubernetes.getNamespace()
}

@NonCPS
def getImageStreamSha(imageStreamName) {
  echo '1'
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
def addAnnotationToBuild(buildName, annotation, value) {
  def flow = new Fabric8Commands()
  if (flow.isOpenShift()) {
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
    def flow = new Fabric8Commands()
    if (flow.isOpenShift()) {
      // avoid annotating builds until we use the new helper methods to get the build name
      //def buildName = getValidOpenShiftBuildName()
      //addAnnotationToBuild(buildName, 'fabric8.io/pipeline.type', t)
    }
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
    OpenShiftClient oClient = new DefaultOpenShiftClient();
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

  def jobName = env.JOB_NAME
  if (jobName.contains('/')){
    jobName = jobName.substring(0, jobName.lastIndexOf('/'))
    jobName = jobName.replace('/','.')
  }

  def buildName = jobName + '-' + env.BUILD_NUMBER
  buildName = buildName.substring(buildName.lastIndexOf("/") + 1).toLowerCase()
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
return this
