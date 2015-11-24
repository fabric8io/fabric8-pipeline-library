def failIfNoTests = ""
try {
  failIfNoTests = ITEST_FAIL_IF_NO_TEST
} catch (Throwable e) {
  failIfNoTests = "false"
}

def itestPattern = ""
try {
  itestPattern = ITEST_PATTERN
} catch (Throwable e) {
  itestPattern = "*KT"
}

def versionPrefix = ""
try {
  versionPrefix = VERSION_PREFIX
} catch (Throwable e) {
  versionPrefix = "1.0"
}

def canaryVersion = "${versionPrefix}.${env.BUILD_NUMBER}"

node ('swarm'){
  git GIT_URL

  withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

    mavenCanaryRelease{
      version = canaryVersion
    }

    mavenIntegrationTest{
      environment = 'Testing'
      failIfNoTests = localFailIfNoTests
      itestPattern = localItestPattern
    }

    mavenRollingUpgrade{
      environment = 'Staging'
      stageDomain = STAGE_DOMAIN
    }
  }
}
