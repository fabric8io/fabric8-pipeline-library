def localItestPattern = ""
try {
  localItestPattern = ITEST_PATTERN
} catch (Throwable e) {
  localItestPattern = "*KT"
}

def localFailIfNoTests = ""
try {
  localFailIfNoTests = ITEST_FAIL_IF_NO_TEST
} catch (Throwable e) {
  localFailIfNoTests = "false"
}

def versionPrefix = ""
try {
  versionPrefix = VERSION_PREFIX
} catch (Throwable e) {
  versionPrefix = "1.0"
}

def canaryVersion = "${versionPrefix}.${env.BUILD_NUMBER}"

def fabric8Console = "${env.FABRIC8_CONSOLE ?: ''}"

node ('kubernetes'){
  git GIT_URL

  // lets install maven onto the path
  withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

    mavenCanaryRelease{
      version = canaryVersion
    }

    // TODO docker push?

    mavenIntegrationTest{
      environment = 'Testing'
      failIfNoTests = localFailIfNoTests
      itestPattern = localItestPattern
    }

    mavenRollingUpgrade{
      environment = 'Staging'
      stageDomain = STAGE_DOMAIN
    }

    approve{
      room = null
      version = canaryVersion
      console = fabric8Console
      environment = 'Staging'
    }

    mavenRollingUpgrade{
      environment = 'Production'
      stageDomain = PROMOTE_DOMAIN
    }
  }
}
