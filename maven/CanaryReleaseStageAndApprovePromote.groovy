node ('swarm'){
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

    def flow = new io.fabric8.Release()
    def fabricMavenPluginVersion = flow.getReleaseVersion "fabric8-maven-plugin"
    def dockerMavenPluginVersion = '0.13.6'

    def canaryVersion = "${versionPrefix}.${env.BUILD_NUMBER}"
    sh "git checkout -b ${env.JOB_NAME}-${canaryVersion}"
    sh "mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -DnewVersion=${canaryVersion}"
    sh "mvn clean install org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy org.jolokia:docker-maven-plugin:${dockerMavenPluginVersion}:build -Dfabric8.dockerUser=fabric8/ -Ddocker.registryPrefix=docker.io/"

    def fabric8Console = "${env.FABRIC8_CONSOLE ?: ''}"

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

    sh "mvn org.apache.maven.plugins:maven-failsafe-plugin:2.18.1:integration-test -Dit.test=${itestPattern} -DfailIfNoTests=${failIfNoTests} org.apache.maven.plugins:maven-failsafe-plugin:2.18.1:verify -Ddocker.registryPrefix=docker.io/"


    stage 'stage'

    // now lets stage it
    echo "Staging to kubernetes environment ${stageNamespace} in domain ${stageDomain}"
    sh "mvn io.fabric8:fabric8-maven-plugin:${fabricMavenPluginVersion}:json io.fabric8:fabric8-maven-plugin:${fabricMavenPluginVersion}:apply -Dfabric8.namespace=${stageNamespace} -Dfabric8.domain=${stageDomain} -Dfabric8.dockerUser=fabric8/ -Ddocker.registryPrefix=docker.io/"

    stage 'approve'


    def proceedMessage = """Version ${canaryVersion} has now been staged to the ${stageNamespace} namespace at:
${fabric8Console}/kubernetes/pods?namespace=${stageNamespace}

Would you like to promote version ${canaryVersion} to the ${promoteNamespace} namespace?
"""
    hubotApprove proceedMessage
    input id: 'Proceed', message: "\n${proceedMessage}"

    stage 'promote'

    echo "Promoting to kubernetes environment ${promoteNamespace} in domain ${promoteDomain}"
    sh "mvn io.fabric8:fabric8-maven-plugin:${fabricMavenPluginVersion}:apply -Dfabric8.namespace=${promoteNamespace} -Dfabric8.domain=${promoteDomain} -Dfabric8.dockerUser=fabric8/ -Ddocker.registryPrefix=docker.io/"

    echo """

Version ${canaryVersion} has now been promoted to the ${promoteNamespace} namespace
View the Promoted environment at:
${fabric8Console}/kubernetes/pods?namespace=${promoteNamespace}

"""
  }
}
