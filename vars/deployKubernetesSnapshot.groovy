#!/usr/bin/groovy
import com.cloudbees.groovy.cps.NonCPS

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def yaml
    def mavenRepo = config.mavenRepo
    def namespace = config.namespace
    def originalImageName = config.originalImageName
    def newImageName = config.newImageName
    def providerLabel = config.providerLabel ?: 'fabric8'
    def project = config.project
    def appToDeploy = config.appToDeploy
    def clusterName = config.clusterName
    def clusterZone = config.clusterZone
    def extraYAML = config.extraYAML
    def templateParameters = config.templateParameters

    def flow = new io.fabric8.Fabric8Commands()
    def utils = new io.fabric8.Utils()

    namespace = namespace + '-' + utils.getRepoName()
    container('k8s') {
        if (!flow.isAuthorCollaborator("", project)) {
            currentBuild.result = 'ABORTED'
            error 'Change author is not a collaborator on the project, aborting build until we support the [test] comment'
        }

        // get the latest released yaml
        def yamlReleaseVersion = flow.getReleaseVersionFromMavenMetadata("${mavenRepo}/maven-metadata.xml")
        if (templateParameters) {
            yaml = flow.getUrlAsString("${mavenRepo}/${yamlReleaseVersion}/${appToDeploy}-${yamlReleaseVersion}-k8s-template.yml")
        } else {
            yaml = flow.getUrlAsString("${mavenRepo}/${yamlReleaseVersion}/${appToDeploy}-${yamlReleaseVersion}-kubernetes.yml")
        }
        yaml = flow.swizzleImageName(yaml, originalImageName, newImageName)

        if (!yaml.contains(newImageName)) {
            error "original image ${originalImageName} not replaced with ${newImageName} in yaml: \n ${yaml}"
        }
    }

    writeFile file: "./snapshot.yml", text: yaml

    if (templateParameters) {
        writeTemplateValuesToFile(templateParameters)
    }

    container('k8s') {
        sh 'gcloud auth activate-service-account --key-file /root/home/.kube/config.json'
        sh 'gcloud config set container/use_client_certificate True'
        sh "gcloud alpha container clusters get-credentials ${clusterName} -z ${clusterZone}"

        try {
            sh "kubectl get ns ${namespace} | grep Active"
        } catch (err) {
            echo "${err}"
            sh "kubectl create ns ${namespace}"
        }

        if (templateParameters) {
            sh "oc process -n ${namespace} --param-file=./values.txt -f ./snapshot.yml --local | kubectl apply -n ${namespace} -f -"
        } else {
            retry(3) {
                sh "kubectl apply -n ${namespace} -f ./snapshot.yml"
            }
        }

        if (extraYAML) {
            writeFile file: "./extra.yml", text: extraYAML
            retry(3) {
                sh "kubectl apply -n ${namespace} -f ./extra.yml"
            }
        }

        sleep 10
        // ok bad bad but there's a delay between deployments being applied and new pods being started.  lets find a better way to do this.

        waitUntil {
            // something odd with the crt if we retry a few times, lets authenticate each time we try
            sh 'gcloud auth activate-service-account --key-file /root/home/.kube/config.json'
            sh 'gcloud config set container/use_client_certificate True'
            sh "gcloud alpha container clusters get-credentials ${clusterName} -z ${clusterZone}"

            // wait until the pods are running
            try {
                sh "kubectl get pod -l app=${appToDeploy},provider=${providerLabel} -n ${namespace} | grep '1/1       Running'"
                echo "${appToDeploy} pod is running"
                return true
            } catch (err) {
                echo "waiting for ${appToDeploy} to be ready..."
                return false
            }
        }
        def ing
        retry(3) {
            ing = sh(script: "kubectl get ing ${appToDeploy} -o jsonpath=\"{.spec.rules[0].host}\" -n ${namespace}", returnStdout: true).toString().trim()
        }
        return ing
    }
}

def writeTemplateValuesToFile(map) {
    if (map) {
        for (def p in mapToList(map)) {
            echo p.key
            echo p.value
            sh "echo ${p.key}=${p.value} >> ./values.txt"
        }
    }
    map = null
}

// thanks to https://stackoverflow.com/questions/40159258/impossibility-to-iterate-over-a-map-using-groovy-within-jenkins-pipeline#
@NonCPS
def mapToList(depmap) {
    def dlist = []
    for (def entry2 in depmap) {
        dlist.add(new java.util.AbstractMap.SimpleImmutableEntry(entry2.key, entry2.value))
    }
    dlist
}
