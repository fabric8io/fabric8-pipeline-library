// lets allow the VERSION_PREFIX to be specified as a parameter to the build
// but if not lets just default to 1.0
def versionPrefix = ""
try {
  versionPrefix = VERSION_PREFIX
} catch (Throwable e) {
  versionPrefix = "1.0"
}

node ('swarm'){
  git GIT_URL

  // lets install maven onto the path
  withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

    mavenCanaryRelease{
      version = canaryVersion
    }
  }
}
