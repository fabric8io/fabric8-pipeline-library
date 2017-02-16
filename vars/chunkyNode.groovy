#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = "chunky.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    chunkyTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
