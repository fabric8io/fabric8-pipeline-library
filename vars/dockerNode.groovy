#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = "docker.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    dockerTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
