def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()


    stage "promote ${config.project} docker images"
    for(int i = 0; i < config.images.size(); i++){
      node ('swarm'){
        ws ('tag'){
          image = config.images[i]

          sh "docker pull ${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/fabric8/${image}:${config.tag}"
          sh "docker tag -f ${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/fabric8/${image}:${config.tag} docker.io/fabric8/${image}:${config.tag}"

          retry (3){
            sh "docker push -f docker.io/fabric8/${image}:${config.tag}"
          }
        }
      }
    }
  }
