def release = ""
try {
  release = IS_RELEASE
} catch (Throwable e) {
  release = "${env.IS_RELEASE ?: 'false'}"
}

def stagedProjects = []

stage 'update fabric8 release dependency versions'
pullRequest = bumpFabric8Versions{}
if (pullRequest != null){
  waitUntilPullRequestMerged{
    name = 'fabric8'
    prId = pullRequest
  }
}

stage 'stage fabric8'
stagedProjects << stageProject{
  project = 'fabric8'
}

stage 'release fabric8'
fabric8ReleasePR = release {
  projectStagingDetails = stagedProjects
  project = 'fabric8'
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
  devopsPr = bumpDevOpsVersions{}
  if (devopsPr != null){
    waitUntilPullRequestMerged{
      name = 'fabric8-devops'
      prId = devopsPr
    }
  }
}, ipaas: {
  ipaasPr = bumpiPaaSVersions{}
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
    def quickstartsReleasePR = release {
      projectStagingDetails = stagedProjects
      project = 'ipaas-quickstarts'
    }
  }, fabric8DevOps: {
    def devopsReleasePR = release {
      projectStagingDetails = stagedProjects
      project = 'fabric8-devops'
    }
  }, fabric8iPaaS: {
    def ipaasReleasePR = release {
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
    waitUntilArtifactSyncedWithCentral {
      artifact = 'fabric8-maven-plugin'
    }
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

hubotProject "release finished"
