#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = "nodejs.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    nodejsTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
