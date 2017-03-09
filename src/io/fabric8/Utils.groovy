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
//
//IMAGE_STREAM_TAG_RETRIES = 15;
//IMAGE_STREAM_TAG_RETRY_TIMEOUT_IN_MILLIS = 1000;

@NonCPS
def environmentNamespace(environment) {
  KubernetesClient kubernetes = new DefaultKubernetesClient()
  return kubernetes.getNamespace() + "-${environment}"
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
    OpenShiftClient oClient = new DefaultOpenShiftClient();
    oClient.builds().withName(buildName).edit().editMetadata().addToAnnotations(annotation, value).endMetadata().done()
  } else {
    echo "No running on openshift so skip adding annotation ${annotation}: value"
  }
}

def isCI(){

  // if we are running a branch plugin generated job check the env var
  if (env.BRANCH_NAME){
    if (env.BRANCH_NAME.startsWith('PR-')) {
      return true
    }
    return false
  }

  // otherwise if we aren't running on master then this is a CI build
  def branch = sh(script: 'git symbolic-ref --short -q HEAD', returnStdout: true).toString().trim()

  if (branch.equals('master')) {
    return false
  }
  return true
}

def isCD(){

  // if we are running a branch plugin generated job check the env var
  if (env.BRANCH_NAME){
    if (env.BRANCH_NAME.equals('master')) {
      return true
    }
    return false
  }

  // otherwise if we are running on master then this is a CD build
  def branch = sh(script: 'git symbolic-ref --short -q HEAD', returnStdout: true).toString().trim()

  if (branch.equals('master')) {
    return true
  }
  return false
}

def getLatestVersionFromTag(){
  sh "git config versionsort.prereleaseSuffix -RC"
  sh "git config versionsort.prereleaseSuffix -M"

  // if the repo has no tags this command will fail
  def version = sh(script: 'git tag --sort version:refname | tail -1', returnStdout: true).toString().trim()

  if (version == null || version.size() == 0){
    error 'no release tag found'
  }
  return version.startsWith("v") ? version.substring(1) : version
}

return this
