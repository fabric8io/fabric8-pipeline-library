#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def name = config.projectStagingDetails[0]
    def version = config.projectStagingDetails[1]
    def repoIds = config.projectStagingDetails[2]

    // use hash to avoid incompatible chars
    unstash name:"staged-${config.project}-${version}".hashCode().toString()

    kubernetes.pod('buildpod').withImage('fabric8/maven-builder:1.1')
    .withPrivileged(true)
    .withSecret('jenkins-maven-settings','/root/.m2')
    .withSecret('jenkins-ssh-config','/root/.ssh')
    .withSecret('jenkins-git-ssh','/root/.ssh-git')
    .inside {

      sh 'chmod 600 /root/.ssh-git/ssh-key'
      sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
      sh 'chmod 700 /root/.ssh-git'

      echo "About to release ${name} repo ids ${repoIds}"
      def flow = new io.fabric8.Fabric8Commands()
      for(int j = 0; j < repoIds.size(); j++){
        flow.releaseSonartypeRepo(repoIds[j])
      }

      if (config.helmPush) {
        flow.helm()
      }

      if (!config.useGitTagForNextVersion){
        flow.updateNextDevelopmentVersion(version)
        return flow.createPullRequest("[CD] Release ${version}","${config.project}")
      }
    }
  }
