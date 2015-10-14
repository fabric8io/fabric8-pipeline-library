def release = ""
try {
  release = IS_RELEASE
} catch (Throwable e) {
  release = "${env.IS_RELEASE ?: 'false'}"
}

def stagedProjects = []

hubot room: 'release', message: "starting fabric8 release pipeline"
try {
  stage 'update fabric8 release dependency versions'
  String fabric8PullRequest = bumpFabric8Versions{}
  if (fabric8PullRequest != null){
    waitUntilPullRequestMerged{
      name = 'fabric8'
      prId = fabric8PullRequest
    }
  }

  stage 'stage fabric8'
  stagedProjects << stageProject{
    project = 'fabric8'
  }

  stage 'release fabric8'
  fabric8ReleasePR = releaseFabric8 {
    projectStagingDetails = stagedProjects
    project = 'fabric8'
  }
  stagedProjects = []
  waitUntilArtifactSyncedWithCentral {
    artifact = 'fabric8-maven-plugin'
  }

  stage 'bump apps and quickstarts release dependency versions'
  parallel(quickstarts: {
    String quickstartPr = bumpiPaaSQuickstartsVersions{}
    if (quickstartPr != null){
      waitUntilPullRequestMerged{
        name = 'ipaas-quickstarts'
        prId = quickstartPr
      }
    }
  }, devops: {
    String devopsPr = bumpFabric8DevOpsVersions{}
    if (devopsPr != null){
      waitUntilPullRequestMerged{
        name = 'fabric8-devops'
        prId = devopsPr
      }
    }
  }, ipaas: {
    String ipaasPr = bumpFabric8iPaaSVersions{}
    if (ipaasPr != null){
      waitUntilPullRequestMerged{
        name = 'fabric8-ipaas'
        prId = ipaasPr
      }
    }
  })

  stage 'stage apps and quickstarts release'
  parallel(quickstarts: {
    stagedProjects << stageProject{
      project = 'ipaas-quickstarts'
    }
  }, devops: {
    stagedProjects << stageProject{
      project = 'fabric8-devops'
    }
  }, ipaas: {
    stagedProjects << stageProject{
      project = 'fabric8-ipaas'
    }
  })

   if (release == 'true'){
    // trigger pull requests
    stage 'release'
     parallel(ipaasQuickstarts: {
        String quickstartsReleasePR = releaseFabric8 {
          projectStagingDetails = stagedProjects
          project = 'ipaas-quickstarts'
        }
     }, fabric8DevOps: {
        String devopsReleasePR = releaseFabric8 {
          projectStagingDetails = stagedProjects
          project = 'fabric8-devops'
        }
      }, fabric8iPaaS: {
        String ipaasReleasePR = releaseFabric8 {
          projectStagingDetails = stagedProjects
          project = 'fabric8-ipaas'
        }
      })

  stage 'wait for fabric8 projects to be synced with maven central and release Pull Requests merged'
   parallel(ipaasQuickstarts: {
      waitUntilArtifactSyncedWithCentral {
        artifact = 'archetypes/archetypes-catalog'
      }
      echo "quickstartsReleasePR is ${quickstartsReleasePR}"
      waitUntilPullRequestMerged{
        name = 'ipaas-quickstarts'
        prId = quickstartsReleasePR
      }

    }, fabric8DevOps: {
      waitUntilArtifactSyncedWithCentral {
        artifact = 'devops/distro/distro'
      }
      echo "devopsReleasePR is ${devopsReleasePR}"
      waitUntilPullRequestMerged{
        name = 'fabric8-devops'
        prId = devopsReleasePR
      }
    }, fabric8iPaaS: {
      waitUntilArtifactSyncedWithCentral {
        artifact = 'ipaas/distro/distro'
      }
      echo "ipaasReleasePR is ${ipaasReleasePR}"
      waitUntilPullRequestMerged{
        name = 'fabric8-ipaas'
        prId = ipaasReleasePR
      }
   })

    stage 'tag fabric8 docker images'
    tagDockerImage{
      images = ['hubot-irc','eclipse-orion','nexus','gerrit','fabric8-kiwiirc','brackets','jenkins-swarm-client','taiga-front','taiga-back','hubot-slack','lets-chat','jenkernetes']
    }

  } else {
    stage 'drop dryrun release'
    dropRelease{
      projects = stagedProjects
    }
  }

  hubot room: 'release', message: "Successfully finished fabric8 release pipeline"
} catch (err){
    hubot room: 'release', message: "Release failed ${err}"
    currentBuild.result = 'FAILURE'
}
