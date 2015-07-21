node {
  git GIT_URL

  // lets install maven onto the path
  withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

    // lets allow the VERSION_PREFIX to be specified as a parameter to the build
    // but if not lets just default to 1.0
    def versionPrefix = ""
    try {
      versionPrefix = VERSION_PREFIX
    } catch (Throwable e) {
      versionPrefix = "1.0"
    }

    // lets allow the STAGE_NAMESPACE to be specified as a parameter to the build
    def stageNamespace = ""
    try {
      stageNamespace = STAGE_NAMESPACE
    } catch (Throwable e) {
      stageNamespace = "${env.JOB_NAME}-staging"
    }

    // lets allow the STAGE_DOMAIN to be specified as a parameter to the build
    def stageDomain = ""
    try {
      stageDomain = STAGE_DOMAIN
    } catch (Throwable e) {
      stageDomain = "${env.JOB_NAME}.${env.KUBERNETES_DOMAIN ?: 'stage.vagrant.f8'}"
    }

    // lets allow the PROMOTE_NAMESPACE to be specified as a parameter to the build
    def promoteNamespace = ""
    try {
      promoteNamespace = PROMOTE_NAMESPACE
    } catch (Throwable e) {
      promoteNamespace = "${env.JOB_NAME}-prod"
    }

    // lets allow the PROMOTE_DOMAIN to be specified as a parameter to the build
    def promoteDomain = ""
    try {
      promoteDomain = PROMOTE_DOMAIN
    } catch (Throwable e) {
      promoteDomain = "${env.JOB_NAME}.${env.KUBERNETES_DOMAIN ?: 'prod.vagrant.f8'}"
    }

    def canaryVersion = "${versionPrefix}.${env.BUILD_NUMBER}"
    sh "git checkout -b ${env.JOB_NAME}-${canaryVersion}"
    sh "mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -DnewVersion=${canaryVersion}"
    sh 'mvn clean install org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy org.jolokia:docker-maven-plugin:0.13.2:build -Dfabric8.dockerUser=fabric8/'

    def fabric8Console = "${env.FABRIC8_CONSOLE ?: ''}"
    def stagingLink = "View the staging environment at ${fabric8Console}/kubernetes/pods?namespace=${stageNamespace}"

    // TODO docker push?

    // now lets stage it
    echo "Staging to kubernetes environment ${stageNamespace} in domain ${stageDomain}"
    sh "mvn io.fabric8:fabric8-maven-plugin:2.2.12:json io.fabric8:fabric8-maven-plugin:2.2.12:apply -Dfabric8.namespace=${stageNamespace} -Dfabric8.domain=${stageDomain} -Dfabric8.dockerUser=fabric8/"

    input """

The version ${canaryVersion} has now been staged to the ${stageNamespace}

${stagingLink}


Warning: about to promote version ${canaryVersion} to the ${promoteNamespace} namespace!!!

Please check out the Staging environment at ${stageNamespace} and decide if you wish to Proceed. Otherwise click Abort!

"""

    echo "Promoting to kubernetes environment ${promoteNamespace} in domain ${promoteDomain}"
    sh "mvn io.fabric8:fabric8-maven-plugin:2.2.12:apply -Dfabric8.namespace=${promoteNamespace} -Dfabric8.domain=${promoteDomain} -Dfabric8.dockerUser=fabric8/"

  }
}
