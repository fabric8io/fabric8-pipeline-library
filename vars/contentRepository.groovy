#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.delegate = config
    body()
    def serviceName = config.serviceName ?: "content-repository";
    def useContentRepository = config.useContentRepository ?: "true"


    if (useContentRepository) {
        def flow = new io.fabric8.Fabric8Commands()
        echo "Checking ${serviceName} exists"
        if (flow.hasService(serviceName)) {
            try {
                //sh 'mvn site site:deploy'
                echo 'mvn site disabled'
            } catch (err) {
                // lets carry on as maven site isn't critical
                echo 'unable to generate maven site'
            }

        } else {
            echo 'no content-repository service so not deploying the maven site report'
        }
    }
}
