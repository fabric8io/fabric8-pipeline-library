def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    // now build, based on the configuration provided
    node {
      ws ('kubernetes-model'){
        withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {

          def flow = new io.fabric8.Release()

          flow.setupWorkspace ('fabric8io/kubernetes-model')

          //if (flow.hasChangedSinceLastRelease()){
            flow.release ("release", config.isRelease)
            flow.updateGithub(config.isRelease)
          //}
        }
      }
    }
}
