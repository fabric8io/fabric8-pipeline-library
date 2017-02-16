#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def projectName = config.stagedProject[0]
    def releaseVersion = config.stagedProject[1]
    def promoteDockerImages = config.imagesToPromoteToDockerHub ?: []
    def tagDockerImages = config.extraImagesToTag ?: []
    def container = config.containerName ?: 'maven'

    String pullRequestId = promoteArtifacts {
      projectStagingDetails = config.stagedProject
      project = projectName
      useGitTagForNextVersion = config.useGitTagForNextVersion
      helmPush = config.helmPush
      containerName = container
    }

    if (promoteDockerImages.size() > 0){
      promoteImages{
        toRegistry = config.promoteToDockerRegistry
        org = config.dockerOrganisation
        project = projectName
        images = promoteDockerImages
        tag = releaseVersion
      }
    }

    if (tagDockerImages.size() > 0){
      tagImages{
        images = tagDockerImages
        tag = releaseVersion
      }
    }

    if (pullRequestId != null){
      waitUntilPullRequestMerged{
        name = config.project
        prId = pullRequestId
      }
    }

    waitUntilArtifactSyncedWithCentral {
      repo = 'http://central.maven.org/maven2/'
      groupId = config.groupId
      artifactId = config.artifactIdToWatchInCentral
      version = releaseVersion
      ext = config.artifactExtensionToWatchInCentral
    }
}
