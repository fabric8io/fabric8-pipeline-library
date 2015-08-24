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

stage 'canary release kubernetes-model'
node {
  ws ('kubernetes-model') {
    // lets install maven onto the path
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
      git "https://github.com/rawlingsj-testproject/kubernetes-model"
      sh "git checkout -b ${env.JOB_NAME}-${canaryVersion}"

      sh "git tag -d \$(git tag)"
      sh "git fetch"
      sh "git reset --hard origin/master"

      //sh "echo your_password | gpg --batch --no-tty --yes --passphrase-fd 0 pubring.gpg"
      sh "mvn -DdryRun=false -Dresume=false release:prepare release:perform -Prelease -DautoVersionSubmodules=true"

      sh "mvn clean install -U -Dgpg.passphrase=${env.GPG_PASSPHRASE}"

      //       REPO_ID=$(mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-list -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org | grep OPEN | grep -Eo 'iofabric8-[[:digit:]]+') && \
      //       mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-close -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${REPO_ID} -Ddescription="Next release is ready" -DstagingProgressTimeoutMinutes=60 && \
      //       mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${REPO_ID} -Ddescription="Next release is ready" -DstagingProgressTimeoutMinutes=60
     }
   }
}


stage 'canary release kubernetes-client'

node {
  ws ('kubernetes-client'){
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
      git "https://github.com/rawlingsj-testproject/kubernetes-client"
      sh "git checkout -b ${env.JOB_NAME}-${canaryVersion}"

      sh "git tag -d \$(git tag)"
      sh "git fetch"
      sh "git reset --hard origin/master"

      sh "mvn -DdryRun=false -Dresume=false release:prepare release:perform -Prelease -DautoVersionSubmodules=true"

      sh "mvn clean install -U -Dgpg.passphrase=${env.GPG_PASSPHRASE}"

      //       REPO_ID=$(mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-list -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org | grep OPEN | grep -Eo 'iofabric8-[[:digit:]]+') && \
      //       mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-close -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${REPO_ID} -Ddescription="Next release is ready" -DstagingProgressTimeoutMinutes=60 && \
      //       mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${REPO_ID} -Ddescription="Next release is ready" -DstagingProgressTimeoutMinutes=60
    }
  }
}

stage 'canary release fabric8'

node {
  ws ('fabric8'){
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
      git "https://github.com/rawlingsj-testproject/fabric8"
      sh "git checkout -b ${env.JOB_NAME}-${canaryVersion}"

      sh "git tag -d \$(git tag)"
      sh "git fetch"
      sh "git reset --hard origin/master"

      sh "mvn -DdryRun=false -Dresume=false release:prepare release:perform -Prelease -DautoVersionSubmodules=true"

      sh "mvn clean install -U -Dgpg.passphrase=${env.GPG_PASSPHRASE}"

      //      REPO_ID=$(mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-list -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org | grep OPEN | grep -Eo 'iofabric8-[[:digit:]]+') && \
      //      mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-close -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${REPO_ID} -Ddescription="Next release is ready" -DstagingProgressTimeoutMinutes=60 && \
      //      mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${REPO_ID} -Ddescription="Next release is ready" -DstagingProgressTimeoutMinutes=60

    }
  }
}

stage 'canary release quickstarts'

node {
  ws ('quickstarts'){
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
      git "https://github.com/rawlingsj-testproject/quickstarts"
      sh "git checkout -b ${env.JOB_NAME}-${canaryVersion}"

      sh "git tag -d \$(git tag)"
      sh "git fetch --tags"
      sh "git reset --hard origin/master"

      sh "mvn -Dresume=false release:prepare release:perform  -Prelease,apps,quickstarts -Ddocker.username=${env.DOCKER_USERNAME} -Ddocker.password=${env.DOCKER_PASSWORD} -Ddocker.registry=docker.io"

      sh "mvn -V -B -U clean install -Dgpg.passphrase=${env.GPG_PASSPHRASE}"

      //      REPO_ID=$(mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-list -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org | grep OPEN | grep -Eo 'iofabric8-[[:digit:]]+') && \
      //      mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-close -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${REPO_ID} -Ddescription="Next release is ready" -DstagingProgressTimeoutMinutes=60 && \
      //      mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${REPO_ID} -Ddescription="Next release is ready" -DstagingProgressTimeoutMinutes=60

    }
  }
}
