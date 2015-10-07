def release = ""
try {
  release = IS_RELEASE
} catch (Throwable e) {
  release = "${env.IS_RELEASE ?: 'false'}"
}

def stagedProjects = []
hubot room: 'release', message: "starting release"
try {
  stagedProject.name = 'fabric8'
  stagedProject.version = '2.2.46'
  stagedProject.repoId = ['iofabric8-1655']
  stagedProjects << stagedProject

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
    quickstartPr = bumpiPaaSQuickstartsVersions{}
    if (quickstartPr != null){
      waitUntilPullRequestMerged{
        name = 'ipaas-quickstarts'
        prId = quickstartPr
      }
    }
  }, devops: {
    devopsPr = bumpFabric8DevOpsVersions{}
    if (devopsPr != null){
      waitUntilPullRequestMerged{
        name = 'fabric8-devops'
        prId = devopsPr
      }
    }
  }, ipaas: {
    ipaasPr = bumpFabric8iPaaSVersions{}
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

  // stage 'run system test'
  // runSystemTests{
  //   projects = stagedProjects
  //}

  if (release == true){
    // trigger pull requests
    echo 'would be a release'
    stage 'release'
    parallel(ipaasQuickstarts: {
      def quickstartsReleasePR = releaseFabric8 {
        projectStagingDetails = stagedProjects
        project = 'ipaas-quickstarts'
      }
    }, fabric8DevOps: {
      def devopsReleasePR = releaseFabric8 {
        projectStagingDetails = stagedProjects
        project = 'fabric8-devops'
      }
    }, fabric8iPaaS: {
      def ipaasReleasePR = releaseFabric8 {
        projectStagingDetails = stagedProjects
        project = 'fabric8-ipaas'
      }
    })

    stage 'wait for fabric8 projects to be synced with maven central and release Pull Requests merged'
    parallel(model: {
      waitUntilArtifactSyncedWithCentral {
        artifact = 'kubernetes-model'
      }
      waitUntilPullRequestMerged{
        name = 'kubernetes-model'
        prId = modelReleasePR
      }
    }, client: {
      waitUntilArtifactSyncedWithCentral {
        artifact = 'kubernetes-client'
      }
      waitUntilPullRequestMerged{
        name = 'kubernetes-client'
        prId = clientPRPr
      }
    }, fabric8: {
      waitUntilPullRequestMerged{
        name = 'fabric8'
        prId = fabric8ReleasePR
      }
    }, ipaasQuickstarts: {
      waitUntilArtifactSyncedWithCentral {
        artifact = 'archetypes/archetypes-catalog'
      }
      waitUntilPullRequestMerged{
        name = 'ipaas-quickstarts'
        prId = quickstartsReleasePR
      }
    }, fabric8DevOps: {
      waitUntilArtifactSyncedWithCentral {
        artifact = 'devops/distro/distro'
      }
      waitUntilPullRequestMerged{
        name = 'fabric8-devops'
        prId = devopsReleasePR
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

    stage 'tag fabric8 docker images'
    dockerImages = 'hubot-irc' << 'eclipse-orion' << 'nexus' << 'gerrit' << 'fabric8-kiwiirc' << 'brackets' << 'jenkins-swarm-client' << 'taiga-front' << 'taiga-back' << 'hubot-slack' << 'lets-chat' << 'jenkernetes'
    tagDockerImage{
      images = dockerImages
    }

  } else {
    stage 'drop dryrun release'
    dropRelease{
      projects = stagedProjects
    }
  }

  hubot room: 'release', message: "Release was successful"
} catch (err){
    hubot room: 'release', message: "Release failed ${err}"
    currentBuild.result = 'FAILURE'
}
