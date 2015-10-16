stage 'test push'
node ('swarm'){
  ws ('push'){
    sh 'git clone git@github.com:rawlingsj/testDockerfile.git'
    sh 'cd testDockerfile'
  }
}
