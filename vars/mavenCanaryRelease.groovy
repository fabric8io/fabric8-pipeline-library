#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    def utils = new io.fabric8.Utils()

    def skipTests = config.skipTests ?: false

    def profile
    if (flow.isOpenShift()) {
        profile = '-P openshift'
    } else {
        profile = '-P kubernetes'
    }

    sh "git checkout -b ${env.JOB_NAME}-${config.version}"
    sh "mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -U -DnewVersion=${config.version}"
    sh "mvn clean -e -U deploy -Dmaven.test.skip=${skipTests} ${profile}"


    junitResults(body);

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

        bayesianScanner(body);
    }



    sonarQubeScanner(body);


    def s2iMode = flow.isOpenShiftS2I()
    echo "s2i mode: ${s2iMode}"

    def registryHost = env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST
    def registryPort = env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT

    if (!s2iMode) {
        if (flow.isSingleNode()) {
            echo 'Running on a single node, skipping docker push as not needed'
            def m = readMavenPom file: 'pom.xml'
            def groupId = m.groupId.split('\\.')
            def user = groupId[groupId.size() - 1].trim()
            def artifactId = m.artifactId
            if (registryHost && registryPort) {
                sh "docker tag ${user}/${artifactId}:${config.version} ${registryHost}:${registryPort}/${user}/${artifactId}:${config.version}"
            } else {
                echo "WARNING: cannot tag the docker image ${user}/${artifactId}:${config.version} as there is no FABRIC8_DOCKER_REGISTRY_SERVICE_HOST or FABRIC8_DOCKER_REGISTRY_SERVICE_PORT environment variable!"
            }
        } else {
            if (registryHost && registryPort) {
                retry(3) {
                    sh "mvn fabric8:push -Ddocker.push.registry=${registryHost}:${registryPort}"
                }
            } else {
                error "Cannot push the docker image ${user}/${artifactId}:${config.version} as there is no FABRIC8_DOCKER_REGISTRY_SERVICE_HOST or FABRIC8_DOCKER_REGISTRY_SERVICE_PORT environment variables\nTry run the fabric8-docker-registry?"
            }
        }
    }

    contentRepository(body);
  }
