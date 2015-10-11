def project = 'fabric8-devops'
node ('swarm'){
  ws (project){
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

      def flow = new io.fabric8.Release()
      flow.setupWorkspace (project)

      def uid = UUID.randomUUID().toString()
      sh "git checkout -b versionUpdate${uid}"

      def updated = false
      def fabric8Version = flow.getReleaseVersion "fabric8-maven-plugin"
      try {
        flow.searchAndReplaceMavenVersionProperty("<fabric8.version>", fabric8Version)
        updated = true
      } catch (err) {
        echo "Already on the latest versions of fabric8 dependencies"
      }


        def parsedVersion = fabric8Version.split('\\.')
        def nextFabric8DevelopmentSnapshotVersion = (parsedVersion[2].toInteger() + 1)
        flow.searchAndReplaceMavenSnapshotProfileVersionProperty("<fabric8.version>", parsedVersion[0] + '.' + parsedVersion[1] + '.' + nextFabric8DevelopmentSnapshotVersion)
        updated = true

        echo "Already on the latest SNAPSHOT versions of fabric8 dependencies"

    }
  }
}


// def release = ""
// try {
//   release = IS_RELEASE
// } catch (Throwable e) {
//   release = "${env.IS_RELEASE ?: 'false'}"
// }
//
// def stagedProjects = []
//
// hubot room: 'release', message: "starting release"
// try {
//
// //stagedProjects << ['fabric8-devops','2.2.47','1']
// //stagedProjects << ['fabric8-ipaas','2.2.47','1']
//
//   //  if (release == 'true'){
//   //   // trigger pull requests
//   //   stage 'release'
//   //    parallel(fabric8DevOps: {
//   //       String devopsReleasePR = releaseFabric8 {
//   //         projectStagingDetails = stagedProjects
//   //         project = 'fabric8-devops'
//   //       }
//   //     }, fabric8iPaaS: {
//   //       String ipaasReleasePR = releaseFabric8 {
//   //         projectStagingDetails = stagedProjects
//   //         project = 'fabric8-ipaas'
//   //       }
//   //     })
//    //
//
//
// String quickstartsReleasePR = '805'
// String devopsReleasePR = '98'
// String ipaasReleasePR = '41'
//
//   stage 'wait for fabric8 projects to be synced with maven central and release Pull Requests merged'
//   //  parallel(ipaasQuickstarts: {
//   //     waitUntilArtifactSyncedWithCentral {
//   //       artifact = 'archetypes/archetypes-catalog'
//   //     }
//   //     echo "quickstartsReleasePR is ${quickstartsReleasePR}"
//   //     waitUntilPullRequestMerged{
//   //       name = 'ipaas-quickstarts'
//   //       prId = quickstartsReleasePR
//   //     }
//    //
//   //   }, fabric8DevOps: {
//   //     waitUntilArtifactSyncedWithCentral {
//   //       artifact = 'devops/distro/distro'
//   //     }
//   //     echo "devopsReleasePR is ${devopsReleasePR}"
//   //     waitUntilPullRequestMerged{
//   //       name = 'fabric8-devops'
//   //       prId = devopsReleasePR
//   //     }
//   //   }, fabric8iPaaS: {
//       waitUntilArtifactSyncedWithCentral {
//         artifact = 'ipaas/distro/distro'
//       }
//       echo "ipaasReleasePR is ${ipaasReleasePR}"
//       waitUntilPullRequestMerged{
//         name = 'fabric8-ipaas'
//         prId = ipaasReleasePR
//       }
//    //})
//
//     stage 'tag fabric8 docker images'
//     tagDockerImage{
//       images = ['hubot-irc','eclipse-orion','nexus','gerrit','fabric8-kiwiirc','brackets','jenkins-swarm-client','taiga-front','taiga-back','hubot-slack','lets-chat','jenkernetes']
//     }
//
//
//
//   hubot room: 'release', message: "Release was successful"
// } catch (err){
//     hubot room: 'release', message: "Release failed ${err}"
//     currentBuild.result = 'FAILURE'
// }
