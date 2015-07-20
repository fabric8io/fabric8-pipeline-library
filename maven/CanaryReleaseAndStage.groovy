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
      stageDomain = "${env.JOB_NAME}.${env.KUBERNETES_DOMAIN ?: 'vagrant.f8'}"
    }

    def canaryVersion = "${versionPrefix}.${env.BUILD_NUMBER}"
    sh "git checkout -b ${env.JOB_NAME}-${canaryVersion}"
    sh "mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -DnewVersion=${canaryVersion}"
    sh 'mvn clean install org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy org.jolokia:docker-maven-plugin:0.13.2:build -Dfabric8.dockerUser=fabric8/'

    // TODO docker push?

    // now lets stage it
    echo "Now staging to kubernetes environment ${stageNamespace} in domain ${stageDomain}"
    sh "mvn io.fabric8:fabric8-maven-plugin:2.2.11:json io.fabric8:fabric8-maven-plugin:2.2.11:apply -Dfabric8.namespace=${stageNamespace} -Dfabric8.domain=${stageDomain} -Dfabric8.dockerUser=fabric8/"
  }
}
