def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    stage "tag ${config.project} docker images"
    node ('swarm'){
      ws ('tag'){
        def flow = new io.fabric8.Fabric8Commands()

        def tag
        def images = []

        if (config.project == 'fabric8-devops'){
          tag = flow.getReleaseVersion('io/fabric8/devops/distro/distro')
          images = ['fabric8-console','hubot-irc','eclipse-orion','nexus','gerrit','fabric8-kiwiirc','brackets','jenkins-swarm-client','taiga-front','taiga-back','hubot-slack','lets-chat','jenkernetes']
        }

        for(int i = 0; i < images.size(); i++){
          image = images[i]

          // first try and find an image marked as release
          try {
            sh "docker pull docker.io/fabric8/${image}:staged"
            sh "docker tag -f docker.io/fabric8/${image}:staged docker.io/fabric8/${image}:${tag}"
          } catch (err) {
            hubot room: 'release', message: "WARNING No staged tag found for image ${image} so will apply release tag to :latest"
            sh "docker pull docker.io/fabric8/${image}:latest"
            sh "docker tag -f docker.io/fabric8/${image}:latest docker.io/fabric8/${image}:${tag}"
          }

          sh "docker push -f docker.io/fabric8/${image}:${tag}"
        }
      }
    }
  }
