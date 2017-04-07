#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (!config.pullRequestProject){
        error 'No project details provided to get Pull Request comments'
    }

    if (!env.CHANGE_ID){
        error 'No Change ID found, is this a Pull Request?'
    }

    def runtimeDir = pwd()
    def utils = new io.fabric8.Utils()
    def downstreamfabric8UIOrg = utils.getDownstreamProjectOverrides(config.pullRequestProject, env.CHANGE_ID, 'fabric8-ui') ?: 'fabric8io'
    echo "using ${downstreamfabric8UIOrg}/fabric8-ui"

    sh "git clone https://github.com/${downstreamfabric8UIOrg}/fabric8-ui"
    sh 'cd fabric8-ui && npm install'
    sh "cd fabric8-ui && npm install --save  ${runtimeDir}/dist"
    sh '''
        export FABRIC8_WIT_API_URL="https://api.prod-preview.openshift.io/api/"
        export FABRIC8_RECOMMENDER_API_URL="https://api-bayesian.dev.rdu2c.fabric8.io/api/v1/"
        export FABRIC8_FORGE_API_URL="https://forge.api.prod-preview.openshift.io"
        export FABRIC8_SSO_API_URL="https://sso.prod-preview.openshift.io/"

        cd fabric8-ui && npm run build:prod
        '''
    def shortCommitSha = getNewVersion {}
    def tempVersion= 'SNAPSHOT.' + shortCommitSha + env.BUILD_NUMBER
    return tempVersion
}
