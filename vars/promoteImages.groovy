#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (!config.org) {
        error 'Docker Organisation config missing'
    }

    if (!config.toRegistry) {
        error 'Promote To Docker Registry config missing'
    }

    container(name: 'docker') {

        for (int i = 0; i < config.images.size(); i++) {

            image = config.images[i]

            def flow = new io.fabric8.Fabric8Commands()
            // if we're running on a single node then we already have the image on this host so no need to pull image
            if (flow.isSingleNode()) {
                sh "docker tag ${config.org}/${image}:${config.tag} ${config.toRegistry}/${config.org}/${image}:${config.tag}"
            } else {
                sh "docker pull ${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/fabric8/${image}:${config.tag}"
                sh "docker tag ${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${config.org}/${image}:${config.tag} ${config.toRegistry}/${config.org}/${image}:${config.tag}"
            }

            retry(3) {
                sh "docker push ${config.toRegistry}/${config.org}/${image}:${config.tag}"
            }
        }
    }
}
