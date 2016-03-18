#!/usr/bin/groovy
def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  sh "git remote set-url origin git@github.com:${config.project}.git"

  def flow = new io.fabric8.Fabric8Commands()
  def repoId
  def releaseVersion

  kubernetes.pod('buildpod').withImage('fabric8/maven-builder:1.1')
  .withPrivileged(true)
  .withHostPathMount('/var/run/docker.sock','/var/run/docker.sock')
  .withEnvVar('DOCKER_CONFIG','/root/.docker/')
  .withSecret('jenkins-maven-settings','/root/.m2')
  .withSecret('jenkins-ssh-config','/root/.ssh')
  .withSecret('jenkins-git-ssh','/root/.ssh-git')
  .withSecret('jenkins-release-gpg','/root/.gnupg')
  .withSecret('jenkins-docker-cfg','/root/.docker')
  .inside {

    sh 'chmod 600 /root/.ssh-git/ssh-key'
    sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
    sh 'chmod 700 /root/.ssh-git'
    sh 'chmod 600 /root/.gnupg/pubring.gpg'
    sh 'chmod 600 /root/.gnupg/secring.gpg'
    sh 'chmod 600 /root/.gnupg/trustdb.gpg'
    sh 'chmod 700 /root/.gnupg'

    flow.setupWorkspaceForRelease(config.project, config.useGitTagForNextVersion)

    repoId = flow.stageSonartypeRepo()
    releaseVersion = flow.getProjectVersion()

    stash excludes: '*/src/', includes: '**', name: "staged-${config.project}-${releaseVersion}".hashCode().toString()

    if (!config.useGitTagForNextVersion){
      flow.updateGithub ()
    }

    return [config.project, releaseVersion, repoId]
  }

}
