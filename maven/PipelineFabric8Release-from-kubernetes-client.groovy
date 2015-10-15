def release = ""
try {
  release = IS_RELEASE
} catch (Throwable e) {
  release = "${env.IS_RELEASE ?: 'false'}"
}

def stagedProjects = []

hubot room: 'release', message: "starting release pipeline from kubernetes-client"
try {
  String clientPullRequest = bumpKubernetesClientVersions{}
  if (clientPullRequest != null){
    waitUntilPullRequestMerged{
      name = 'kubernetes-client'
      prId = clientPullRequest
    }
  }

  stagedProjects << stageProject{
    project = 'kubernetes-client'
  }

  clientReleasePR = releaseFabric8 {
    projectStagingDetails = stagedProjects
    project = 'kubernetes-client'
  }

  stagedProjects = []
  waitUntilArtifactSyncedWithCentral {
    artifact = 'kubernetes-client'
  }

  String fabric8PullRequest = bumpFabric8Versions{}
  if (fabric8PullRequest != null){
    waitUntilPullRequestMerged{
      name = 'fabric8'
      prId = fabric8PullRequest
    }
  }

  stagedProjects << stageProject{
    project = 'fabric8'
  }

  fabric8ReleasePR = releaseFabric8 {
    projectStagingDetails = stagedProjects
    project = 'fabric8'
  }
  stagedProjects = []
  waitUntilArtifactSyncedWithCentral {
    artifact = 'fabric8-maven-plugin'
  }

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
     def quickstartsReleasePR = ""
     def devopsReleasePR = ""
     def ipaasReleasePR = ""
     
     parallel(ipaasQuickstarts: {
        quickstartsReleasePR = releaseFabric8 {
          projectStagingDetails = stagedProjects
          project = 'ipaas-quickstarts'
        }
     }, fabric8DevOps: {
        devopsReleasePR = releaseFabric8 {
          projectStagingDetails = stagedProjects
          project = 'fabric8-devops'
        }
      }, fabric8iPaaS: {
        ipaasReleasePR = releaseFabric8 {
          projectStagingDetails = stagedProjects
          project = 'fabric8-ipaas'
        }
      })

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
      tagDockerImage{
        project = 'fabric8-devops'
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

  } else {
    dropRelease{
      projects = stagedProjects
    }
  }

  hubot room: 'release', message: "Successfully finished release pipeline"
} catch (err){
    hubot room: 'release', message: "Release failed ${err}"
    currentBuild.result = 'FAILURE'
}
