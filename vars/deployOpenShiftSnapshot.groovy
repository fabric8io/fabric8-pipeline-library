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

    def flow = new io.fabric8.Fabric8Commands()

    container('clients'){
        // get the latest released yaml
        def yamlReleaseVersion = flow.getReleaseVersionFromMavenMetadata("${mavenRepo}/maven-metadata.xml")
        yaml = flow.getUrlAsString("${mavenRepo}/${yamlReleaseVersion}/${deploymentName}-${yamlReleaseVersion}-openshift.yml")

        yaml = flow.swizzleImageName(yaml,originalImageName,newImageName)

    }
    // cant use writeFile as we have long filename errors
    sh "echo '${yaml}' > snapshot.yml"

    container('clients'){

        try {
            sh "oc get project ${openShiftProject} | grep Active"
        } catch (err){
            echo "${err}"
            sh "oc new-project ${openShiftProject}"
        }

        sh "oc process -n ${openShiftProject} -f ./snapshot.yml | oc apply -n ${openShiftProject} -f -"

        sleep 10 // ok bad bad but there's a delay between DC's being applied and new pods being started.  lets find a better way to do this looking at teh new DC perhaps?

        waitUntil{
            // wait until the pods are running has been deleted
            try{
                sh "oc get pod -l project=${deploymentName},provider=${providerLabel} -n ${openShiftProject} | grep Running"
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
