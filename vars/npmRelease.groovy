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

    sh """
       git config user.email ${gitEmail}
       git config user.name ${gitUserName}

       install -m 600 -D /root/.ssh-git-ro/ssh-key /root/.ssh-git/ssh-key
       install -m 600 -D /root/.ssh-git-ro/ssh-key.pub /root/.ssh-git/ssh-key.pub
       """

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
            echo "ERROR publishing: ${err.getMessage()}"
            echo "No artifacts published so skip updating downstream projects"
            return false
        }
        return true
    }
}
