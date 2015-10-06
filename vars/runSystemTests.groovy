def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()


  for(int i = 0; i < stagedProjects.size(); i++){
    // currently system tests only exist fot fabric8-devops
    if (stagedProjects[i].name == 'fabric8-devops'){
      node (swarm){
        ws (stagedProjects[i].name){
          withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

            def flow = new io.fabric8.Release()
            flow.setupWorkspace(stagedProjects[i].name)

            def releaseVersion = stagedProjects[i].version
            sh 'git fetch'
            sh "git checkout release-v${releaseVersion}"

            flow.runSystemTests()
          }
        }
      }
    }
  }
}
