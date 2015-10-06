def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    node (swarm){
      ws ('tag'){
        def flow = new io.fabric8.Release()
        def tag = flow.getReleaseVersion('devops/distro/distro')

        for(int i = 0; i < config.images.size(); i++){
          image = config.images[i]
          sh "docker pull docker.io/fabric8/${image}:release"

          // first try and find an image marked as release
          try {
            sg "docker tag docker.io/fabric8/${image}:release docker.io/fabric8/${image}:${tag}"
            sh "docker push docker.io/fabric8/${image}:${tag}"
          } catch (err) {
            hubotProject "WARNING No release tag found for image ${image} so unable to tag new version"
          }
        }
      }
    }
  }
