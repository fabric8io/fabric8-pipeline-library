stage 'test'
node {
   ws ('kubernetes-model') {
    sh "git log --name-status HEAD^..HEAD -1 --grep=\"prepare for next development iteration\" --author='fusesource-ci' >> gitlog.tmp"
    def myfile = readFile('gitlog.tmp')
    sh "rm gitlog.tmp"
    if (myfile.length() == 0 ) {
     currentBuild.result = 'FAILURE'
     echo "failing build"
     return
    } else {
      echo 'continue'
    }
  }
}


// def valid = 'true'
//
// stage 'test-fabric8-devops'
// node {
//    ws ('fabric8-devops') {
//     // lets install maven onto the path
//     withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
//       sh "rm -rf *.*"
//       git 'https://github.com/fabric8io/fabric8-devops'
//
//       sh "git remote set-url origin git@github.com:fabric8io/fabric8-devops.git"
//       sh "git config user.email fabric8-admin@googlegroups.com"
//       sh "git config user.name fusesource-ci"
//
//
//       sh "git tag -d \$(git tag)"
//       sh "git fetch --tags"
//       sh "git reset --hard origin/master"
//
//       def test = "test-tag4"
//       sh "git tag -a ${test} -m 'Release version ${test}'"
//       sh "git push origin ${test}"
//
//       sh "echo 'Test for CD release'>> README.md"
//       sh "git commit -a -m 'Dummy commit to test auth from CD infra'"
//       sh "git push origin master"
//
//       sh "git tag -d ${test}"
//       sh "git push origin :refs/tags/${test}"
//     }
//   }
// }
//


// stage 'test'
// node {
//    ws ('kubernetes-model') {
//     // lets install maven onto the path
//     withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
//
//       //sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-list -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org  > repoId.txt"
//       def repoId = readFile('repoId.txt').trim()
//
//       sh "echo ${repoId}"
//       sh "echo ====================================="
//       sh "echo ${repoId}"
//
//     }
//   }
// }
//

// def repoId="iofabric8-1398"
//
// stage 'force release'
// node {
//   ws ('kubernetes-model') {
//     // lets install maven onto the path
//     withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
//         sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
//     }
//   }
// }

// stage 'force version'
// node {
//   ws ('kubernetes-model') {
//     // lets install maven onto the path
//     withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
//
//       git "https://github.com/fabric8io/kubernetes-model"
//
//       sh "git config user.email fabric8-admin@googlegroups.com"
//       sh "git config user.name fusesource-ci"
//
//
//       sh "mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -DnewVersion=1.0.16-SNAPSHOT"
//       sh "git commit -a -m \"[maven-release-plugin] prepare for next development iteration\""
//
//     }
//   }
// }
//
// stage 'force release'
// node {
//      ws ('kubernetes-model') {
//     // lets install maven onto the path
//     withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
//
//         def repoId=sh([script: "${tool 'maven-3.3.1'}/bin mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-list -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org"])
//
//
//         sh "echo ${repoId}"
//         echo "===================================="
//         sh "echo ${repoId}"
//     }
//   }
// }
//

// stage 'force release'
// node {
//      ws ('kubernetes-model') {
//     // lets install maven onto the path
//     withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
//
//         String repoId=sh([script: "set MYID=\$(mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-list -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org | grep OPEN | grep -Eo 'iofabric8-[[:digit:]]+')"])
//
//
//         sh "echo \$MYID"
//         echo "===================================="
//         sh "echo \$MYID"
//     }
//   }
// }
//
// stage 'force release'
// node {
//    ws ('kubernetes-model') {
//     // lets install maven onto the path
//     withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
//
//       //String REPO_ID= sh "\$(mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-list -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org | grep OPEN | grep -Eo 'iofabric8-[[:digit:]]+')"
//       //String REPO_ID= println "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-list -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org".execute().text
//
//
//       sh "${REPO_ID}"
//       sh "================================="
//       sh "${REPO_ID}"
//       //"mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-close -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${REPO_ID} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60 "+
//       //"mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${$REPO_ID} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
//
//     }
//   }
// }
//
//
// stage 'force release'
// node {
//    ws ('kubernetes-model') {
//     // lets install maven onto the path
//     withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
//
//       String openId = "\$(mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-list -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org | grep OPEN | grep -Eo 'iofabric8-[[:digit:]]+')"
//       String closedId = "\$(mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-list -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org | grep CLOSED | grep -Eo 'iofabric8-[[:digit:]]+')"
//
//       sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-close -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${openId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
//       sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${closedId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
//     }
//   }
// }
