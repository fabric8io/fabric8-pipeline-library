#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def yaml
    def mavenRepo = config.mavenRepo
    def openShiftProject = config.openShiftProject
    def originalImageName = config.originalImageName
    def newImageName = config.newImageName
    def deploymentName = config.githubRepo
    def providerLabel = config.providerLabel ?: 'fabric8'
    def project = config.githubProject

    def flow = new io.fabric8.Fabric8Commands()
    def utils = new io.fabric8.Utils()

    openShiftProject = openShiftProject + '-' + utils.getRepoName()
    container('clients') {
        if (!flow.isAuthorCollaborator("", project)){
            currentBuild.result = 'ABORTED'
            error 'Change author is not a collaborator on the project, aborting build until we support the [test] comment'
        }

        // get the latest released yaml

        def yamlReleaseVersion = flow.getReleaseVersionFromMavenMetadata("${mavenRepo}/maven-metadata.xml")
        yaml = flow.getUrlAsString("${mavenRepo}/${yamlReleaseVersion}/${deploymentName}-${yamlReleaseVersion}-openshift.yml")
        yaml = flow.swizzleImageName(yaml, originalImageName, newImageName)

        if (!yaml.contains(newImageName)){
            error "original image ${originalImageName} not replaced with ${newImageName} in yaml: \n ${yaml}"
        }
    }
    // cant use writeFile as we have long filename errors
    sh "echo '${yaml}' > snapshot.yml"
    def template = false
    if (yaml.contains('kind: Template')){
        template = true
    }
    container('clients') {

        try {
            sh "oc get project ${openShiftProject} | grep Active"
        } catch (err) {
            echo "${err}"
            sh "oc new-project ${openShiftProject}"
        }

        // TODO share this code with buildSnapshotFabric8UI.groovy!
        // this is only when deploying fabric8-ui, need to figure out a better way
        sh '''
            export FABRIC8_WIT_API_URL="https://api.prod-preview.openshift.io/api/"
            export FABRIC8_RECOMMENDER_API_URL="https://recommender.api.openshift.io"
            export FABRIC8_FORGE_API_URL="https://forge.api.openshift.io"
            export FABRIC8_SSO_API_URL="https://sso.openshift.io/"
            
            export OPENSHIFT_CONSOLE_URL="https://console.starter-us-east-2.openshift.com/console/"
            export WS_K8S_API_SERVER="api.starter-us-east-2.openshift.com:443"
            
            export PROXIED_K8S_API_SERVER="${WS_K8S_API_SERVER}"
            export OAUTH_ISSUER="https://${WS_K8S_API_SERVER}"
            export PROXY_PASS_URL="https://${WS_K8S_API_SERVER}"
            export K8S_API_SERVER_BASE_PATH="/"
            export OAUTH_AUTHORIZE_URI="https://${WS_K8S_API_SERVER}/oauth/authorize"
            export AUTH_LOGOUT_URI="https://${WS_K8S_API_SERVER}/connect/endsession?id_token={{id_token}}"

            
            echo "FABRIC8_WIT_API_URL=${FABRIC8_WIT_API_URL}" > ./values.txt
            echo "FABRIC8_RECOMMENDER_API_URL=${FABRIC8_RECOMMENDER_API_URL}"  >> ./values.txt
            echo "FABRIC8_FORGE_API_URL=${FABRIC8_FORGE_API_URL}"  >> ./values.txt
            echo "FABRIC8_SSO_API_URL=${FABRIC8_SSO_API_URL}"  >> ./values.txt

            echo "OPENSHIFT_CONSOLE_URL=${OPENSHIFT_CONSOLE_URL}"  >> ./values.txt
            echo "WS_K8S_API_SERVER=${WS_K8S_API_SERVER}"  >> ./values.txt
            echo "K8S_API_SERVER_BASE_PATH=${K8S_API_SERVER_BASE_PATH}"  >> ./values.txt

            echo "PROXIED_K8S_API_SERVER=${PROXIED_K8S_API_SERVER}"  >> ./values.txt
            echo "OAUTH_ISSUER=${OAUTH_ISSUER}"  >> ./values.txt
            echo "PROXY_PASS_URL=${PROXY_PASS_URL}"  >> ./values.txt
            echo "OAUTH_AUTHORIZE_URI=${OAUTH_AUTHORIZE_URI}"  >> ./values.txt
            echo "AUTH_LOGOUT_URI=${AUTH_LOGOUT_URI}"  >> ./values.txt
                
            '''
    // TODO lets use a comment on the PR to denote whether or not to use prod or pre-prod?
    /*
        sh '''
            export FABRIC8_WIT_API_URL="https://api.prod-preview.openshift.io/api/"
            export FABRIC8_RECOMMENDER_API_URL="https://api-bayesian.dev.rdu2c.fabric8.io/api/v1/"
            export FABRIC8_FORGE_API_URL="https://forge.api.prod-preview.openshift.io"
            export FABRIC8_SSO_API_URL="https://sso.prod-preview.openshift.io/"

            export OPENSHIFT_CONSOLE_URL="https://console.free-int.openshift.com/console/"
            export WS_K8S_API_SERVER="api.free-int.openshift.com:443"

            cd fabric8-ui && npm run build:prod
            '''
    */

        if (template){
            sh "oc process -n ${openShiftProject} --param-file=./values.txt -f ./snapshot.yml | oc apply -n ${openShiftProject} -f -"
        } else {
            sh "oc apply -n ${openShiftProject} -f ./snapshot.yml"
        }

        sleep 10
        // ok bad bad but there's a delay between DC's being applied and new pods being started.  lets find a better way to do this looking at teh new DC perhaps?

        waitUntil {
            // wait until the pods are running has been deleted
            try {
                sh "oc get pod -l app=${deploymentName},provider=${providerLabel} -n ${openShiftProject} | grep Running"
                echo "${deploymentName} pod is running"
                return true
            } catch (err) {
                echo "waiting for ${deploymentName} to be ready..."
                return false
            }
        }
        return sh(script: "oc get route ${deploymentName} -o jsonpath=\"{.spec.host}\" -n ${openShiftProject}", returnStdout: true).toString().trim()
    }
}
