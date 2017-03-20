#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    sh 'chmod 600 /root/.ssh-git/ssh-key'
    sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
    sh 'chmod 700 /root/.ssh-git'
    
    sh "git config user.email fabric8-admin@googlegroups.com"
    sh "git config user.name fabric8"

    sh "git tag -fa v${config.releaseVersion} -m 'Release version ${config.releaseVersion}'"
    sh "git push origin v${config.releaseVersion}"
}
