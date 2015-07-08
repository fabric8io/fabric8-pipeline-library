node('docker') {

  docker.image('maven').inside {
    git GIT_URL
    sh 'mvn clean install'
  }
}