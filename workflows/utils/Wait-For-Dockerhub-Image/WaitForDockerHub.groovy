#!/usr/bin/groovy
import groovy.json.JsonSlurper

stage 'wait-for-dockerhub'
node ('kubernetes'){
  waitUntil {
    dockerDockerImageTags("fabric8/jenkernetes").contains('v1')
  }
}

def dockerDockerImageTags(String image) {
  try {
    return "https://registry.hub.docker.com/v1/repositories/${image}/tags".toURL().getText()
  } catch (err) {
    return "NO_IMAGE_FOUND"
  }
}
