#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def branch = config.branch
    def flow = new io.fabric8.Fabric8Commands()

    flow.setupGitSSH()

    String npmToken = readFile '/home/jenkins/.npm-token/token'
    String ghToken = readFile '/home/jenkins/.apitoken/hub'

    wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [
            [password: npmToken, var: 'NPM_PASSWORD'],
            [password: ghToken, var: 'GH_PASSWORD']]]) {

        // GITHUB_TOKEN is necessary for the semantic-release plugin
        // https://github.com/semantic-release/github#environment-variables

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
