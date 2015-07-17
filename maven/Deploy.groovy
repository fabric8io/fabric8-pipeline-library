node {
  git GIT_URL
  withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

    sh 'mvn clean deploy'
  }
}