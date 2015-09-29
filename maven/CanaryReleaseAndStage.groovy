node {
  git GIT_URL

  // lets install maven onto the path
  withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

    stage 'canary release'

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
      stageDomain = "${env.JOB_NAME}.${env.KUBERNETES_DOMAIN ?: 'vagrant.f8'}"
    }

    def fabric8Console = "${env.FABRIC8_CONSOLE ?: ''}"

    def canaryVersion = "${versionPrefix}.${env.BUILD_NUMBER}"
    sh "git checkout -b ${env.JOB_NAME}-${canaryVersion}"
    sh "mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -DnewVersion=${canaryVersion}"
    sh 'mvn clean install -U org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy org.jolokia:docker-maven-plugin:0.13.2:build -Dfabric8.dockerUser=fabric8/'

    // TODO docker push?


    stage 'integration test'

    def itestPattern = ""
    try {
      itestPattern = ITEST_PATTERN
    } catch (Throwable e) {
      itestPattern = "*KT"
    }

    def failIfNoTests = ""
    try {
      failIfNoTests = ITEST_FAIL_IF_NO_TEST
    } catch (Throwable e) {
      failIfNoTests = "false"
    }

    sh "mvn org.apache.maven.plugins:maven-failsafe-plugin:2.18.1:integration-test -Dit.test=${itestPattern} -DfailIfNoTests=${failIfNoTests} org.apache.maven.plugins:maven-failsafe-plugin:2.18.1:verify"


    stage 'stage'

    echo "Now staging to kubernetes environment ${stageNamespace} in domain ${stageDomain}"
    sh "mvn io.fabric8:fabric8-maven-plugin:2.2.35:json io.fabric8:fabric8-maven-plugin:2.2.35:apply -Dfabric8.namespace=${stageNamespace} -Dfabric8.domain=${stageDomain} -Dfabric8.dockerUser=fabric8/"

    echo """

Version ${canaryVersion} has now been staged to the ${stageNamespace} namespace
View the Staging environment at:
${fabric8Console}/kubernetes/pods?namespace=${stageNamespace}

"""
  }
}
