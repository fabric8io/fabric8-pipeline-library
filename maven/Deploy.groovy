node('docker') {

  docker.image('fabric8/maven-nexus').inside {
    git GIT_URL
    sh 'mvn clean deploy'
  }
}