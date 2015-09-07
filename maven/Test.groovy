def repoId="iofabric8-1396"

stage 'force release'
node {
  ws ('kubernetes-model') {
    // lets install maven onto the path
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
        sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
    }
  }
}
