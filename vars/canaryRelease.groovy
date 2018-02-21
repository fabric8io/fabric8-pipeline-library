#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    def utils = new io.fabric8.Utils()



    sh "git checkout -b ${env.JOB_NAME}-${config.version}"

    def buildName = ""
    try {
        buildName = utils.getValidOpenShiftBuildName()
    } catch (err) {
        echo "Failed to find buildName due to: ${err}"
    }

    if (buildName != null && !buildName.isEmpty()) {
        def buildUrl = "${env.BUILD_URL}"
        if (!buildUrl.isEmpty()) {
            utils.addAnnotationToBuild('fabric8.io/jenkins.testReportUrl', "${buildUrl}testReport")
        }
        def changeUrl = env.CHANGE_URL
        if (changeUrl != null && !changeUrl.isEmpty()) {
            utils.addAnnotationToBuild('fabric8.io/jenkins.changeUrl', changeUrl)
        }

        bayesianScanner(body)
    }



    sonarQubeScanner(body)


    def s2iMode = utils.supportsOpenShiftS2I()
    echo "s2i mode: ${s2iMode}"

    if (!s2iMode) {
        def registry = utils.getDockerRegistry()
        if (flow.isSingleNode()) {
            echo 'Running on a single node, skipping docker push as not needed'
            def m = readMavenPom file: 'pom.xml'

            sh "docker tag ${config.version} ${registry}/${config.version}"
        }
    }

    contentRepository(body)
  }
