#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = "deploy.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    deployTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
