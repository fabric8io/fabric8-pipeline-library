def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    // now build, based on the configuration provided
    node ('swarm'){
      def flow = new io.fabric8.Release()

      ws ('test'){
        echo 'working'
        stagedProject.name = config.name
        stagedProject.version = config.version
        stagedProject.repoId = config.repoId

        return stagedProject
      }
    }
}
