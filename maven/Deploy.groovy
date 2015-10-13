node (swarm){
  git GIT_URL
  withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

    sh 'mvn clean install org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy'
  }
}
