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

def getReleaseVersion(String project) {
  def modelMetaData = new XmlSlurper().parse("https://oss.sonatype.org/content/repositories/releases/io/fabric8/"+project+"/maven-metadata.xml")
  def version = modelMetaData.versioning.release.text()
  return version
}

def repoId="\$(mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-list -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org | grep OPEN | grep -Eo 'iofabric8-[[:digit:]]+')"

stage 'canary release kubernetes-model'
node {
  ws ('kubernetes-model') {
    // lets install maven onto the path
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

      git "https://github.com/fabric8io/kubernetes-model"
      sh "git checkout -b ${env.JOB_NAME}-${canaryVersion}"

      sh "git config user.email fabric8-admin@googlegroups.com"
      sh "git config user.name fusesource-ci"

      sh "git tag -d \$(git tag)"
      sh "git fetch"
      sh "git reset --hard origin/master"

      sh "mvn -DdryRun=false -Dresume=false release:prepare release:perform -Prelease -DautoVersionSubmodules=true"
      sh "mvn clean org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy -U -DaltDeploymentRepository=oss.sonatype.org:default:https://oss.sonatype.org/service/local/staging/deploy/maven2/"

      sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-close -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
      sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
     }
   }
}

stage 'canary release kubernetes-client'
node {
  ws ('kubernetes-client'){
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
      git "https://github.com/fabric8io/kubernetes-client"

      sh "git config user.email fabric8-admin@googlegroups.com"
      sh "git config user.name fusesource-ci"

      sh "git checkout -b ${env.JOB_NAME}-${canaryVersion}"

      sh "git tag -d \$(git tag)"
      sh "git fetch"
      sh "git reset --hard origin/master"

      // bump dependency version from the previous stage
      def kubernetesModelVersion = getReleaseVersion("kubernetes-model")
      sh "sed -i -r 's/<kubernetes.model.version>[0-9][0-9]{0,2}.[0-9][0-9]{0,2}.[0-9][0-9]{0,2}/<kubernetes.model.version>${kubernetesModelVersion}/g' pom.xml"
      sh "git commit -a -m 'Bump kubernetes-model version'"

      sh "mvn -DdryRun=false -Dresume=false release:prepare release:perform -Prelease -DautoVersionSubmodules=true"
      sh "mvn clean org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy -U -DaltDeploymentRepository=oss.sonatype.org:default:https://oss.sonatype.org/service/local/staging/deploy/maven2/"

      sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-close -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
      sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
    }
  }
}

stage 'canary release fabric8'
node {
  ws ('fabric8'){
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
      git "https://github.com/fabric8io/fabric8"

      sh "git config user.email fabric8-admin@googlegroups.com"
      sh "git config user.name fusesource-ci"

      sh "git checkout -b ${env.JOB_NAME}-${canaryVersion}"

      sh "git tag -d \$(git tag)"
      sh "git fetch"
      sh "git reset --hard origin/master"

      // bump dependency versions from the previous stage
      def kubernetesClientVersion = getReleaseVersion("kubernetes-client")
      def kubernetesModelVersion = getReleaseVersion("kubernetes-model")
      sh "sed -i -r 's/<kubernetes.model.version>[0-9][0-9]{0,2}.[0-9][0-9]{0,2}.[0-9][0-9]{0,2}/<kubernetes.model.version>${kubernetesModelVersion}/g' pom.xml"
      sh "sed -i -r 's/<kubernetes.client.version>[0-9][0-9]{0,2}.[0-9][0-9]{0,2}.[0-9][0-9]{0,2}/<kubernetes.client.version>${kubernetesClientVersion}/g' pom.xml"
      sh "git commit -a -m 'Bump kubernetes-model and kubernetes-client version'"

      sh "mvn -DdryRun=false -Dresume=false release:prepare release:perform -Prelease -DautoVersionSubmodules=true"
      sh "mvn org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy -U -DaltDeploymentRepository=oss.sonatype.org:default:https://oss.sonatype.org/service/local/staging/deploy/maven2/"

      sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-close -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
      sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
    }
  }
}

stage 'canary release quickstarts'
node {
  ws ('quickstarts'){
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
      git "https://github.com/fabric8io/quickstarts"

      sh "git config user.email fabric8-admin@googlegroups.com"
      sh "git config user.name fusesource-ci"

      sh "git checkout -b ${env.JOB_NAME}-${canaryVersion}"

      sh "git tag -d \$(git tag)"
      sh "git fetch --tags"
      sh "git reset --hard origin/master"

      // bump dependency versions from the previous stage
      def fabric8Version = getReleaseVersion("fabric8-maven-plugin")
      sh "find -type f -name 'pom.xml' | xargs sed -i -r 's/<fabric8.version>[0-9][0-9]{0,2}.[0-9][0-9]{0,2}.[0-9][0-9]{0,2}/<fabric8.version>${fabric8Version}/g'"
      sh "git commit -a -m 'Bump fabric8 version'"

      retry(3) {
          // pushing to dockerhub can fail sometimes so lets retry
          sh "mvn -Dresume=false release:prepare release:perform  -Prelease,apps,quickstarts -Ddocker.username=${env.DOCKER_REGISTRY_USERNAME} -Ddocker.password=${env.DOCKER_REGISTRY_PASSWORD} -Ddocker.registry=docker.io"
      }

      sh "mvn -V -B -U org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy -DaltDeploymentRepository=oss.sonatype.org:default:https://oss.sonatype.org/service/local/staging/deploy/maven2/"

      sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-close -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
      sh "mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repoId} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60"
    }
  }
}
