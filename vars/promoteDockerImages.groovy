def registry = ""
try {
  registry = DOCKER_REGISTRY
} catch (Throwable e) {
  registry = "fabric8-docker-registry.${env.DOMAIN}:80/"
}

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

          sh "docker pull ${registry}/fabric8/${image}:${config.tag}"
          sh "docker tag -f ${registry}/fabric8/${image}:${config.tag} docker.io/fabric8/${image}:${config.tag}"

          retry (3){
            sh "docker push -f docker.io/fabric8/${image}:${config.tag}"
          }
        }
      }
    }
  }
