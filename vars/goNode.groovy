#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = "go.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    goTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
