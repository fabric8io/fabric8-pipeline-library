def release = ""
try {
  release = IS_RELEASE
} catch (Throwable e) {
  release = "${env.IS_RELEASE ?: 'false'}"
}

def stagedProjects = []

hubot room: 'release', message: "starting release pipeline for ipaas-quickstarts, fabric8-devops and fabric8-ipaas"
try {
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

  def quickstartsReleasePR = ""
  
  stagedProjects << stageProject{
    project = 'ipaas-quickstarts'
  }

  quickstartsReleasePR = releaseFabric8 {
    projectStagingDetails = stagedProjects
    project = 'ipaas-quickstarts'
  }

  waitUntilArtifactSyncedWithCentral {
    artifact = 'archetypes/archetypes-catalog'
  }

  waitUntilPullRequestMerged{
    name = 'ipaas-quickstarts'
    prId = quickstartsReleasePR
  }


  parallel(devops: {
    stagedProjects << stageProject{
      project = 'fabric8-devops'
    }
  }, ipaas: {
    stagedProjects << stageProject{
      project = 'fabric8-ipaas'
    }
  })

   if (release == 'true'){

     def devopsReleasePR = ""
     def ipaasReleasePR = ""

     parallel(fabric8DevOps: {
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

   parallel(fabric8DevOps: {
      waitUntilArtifactSyncedWithCentral {
        artifact = 'devops/distro/distro'
      }
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

  hubot room: 'release', message: "Release was successful"
} catch (err){
    hubot room: 'release', message: "Release failed ${err}"
    currentBuild.result = 'FAILURE'
}
