#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def gitEmail = config.gitEmail ?: 'fabric8-admin@googlegroups.com'
    def gitUserName = config.gitUserName ?: 'fabric8-release'
    def branch = config.branch

    sh "git config user.email ${gitEmail}"
    sh "git config user.name ${gitUserName}"

    sh 'chmod 600 /root/.ssh-git/ssh-key'
    sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
    sh 'chmod 700 /root/.ssh-git'

    String npmToken = readFile '/home/jenkins/.npm-token/token'
    String ghToken = readFile '/home/jenkins/.apitoken/hub'
    wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [
        [password: npmToken, var: 'NPM_PASSWORD'],
        [password: ghToken, var: 'GH_PASSWORD']]]) {

        try {
            sh """
            export NPM_TOKEN=${npmToken} 
            export GITHUB_TOKEN=${ghToken}
            export GIT_BRANCH=${branch}
            npm run semantic-release
            """
        } catch (err) {
            echo "ERROR publishing: ${err}"
            echo "No artifacts published so skip updating downstream projects"
            return false
        }
        return true
    }
  }
