#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = "s2i.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    s2iTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
