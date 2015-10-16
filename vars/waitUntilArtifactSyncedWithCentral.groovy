def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    stage "waiting for ${config.artifact} artifacts to sync with central"
    node ('swarm'){
      def flow = new io.fabric8.Fabric8Commands()
      def newVersion = flow.mavenSonartypeReleaseVersion config.artifact

      waitUntil {
        flow.mavenCentralVersion(config.artifact) == newVersion
      }

      message =  "${config.artifact} released and available in maven central"
      hubot room: 'release', message: message

    }
}
