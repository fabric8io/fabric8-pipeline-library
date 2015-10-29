def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    stage "tag ${config.project} docker images"
    for(int i = 0; i < config.images.size(); i++){
      node ('swarm'){
        ws ('tag'){
          image = config.images[i]
          retry (3){
            // first try and find an image marked as release
            try {
              sh "docker pull docker.io/fabric8/${image}:staged"
              sh "docker tag -f docker.io/fabric8/${image}:staged docker.io/fabric8/${image}:${config.tag}"
            } catch (err) {
              hubot room: 'release', message: "WARNING No staged tag found for image ${image} so will apply release tag to :latest"
              sh "docker pull docker.io/fabric8/${image}:latest"
              sh "docker tag -f docker.io/fabric8/${image}:latest docker.io/fabric8/${image}:${config.tag}"
            }

            sh "docker push -f docker.io/fabric8/${image}:${config.tag}"
          }
        }
      }
    }
  }
