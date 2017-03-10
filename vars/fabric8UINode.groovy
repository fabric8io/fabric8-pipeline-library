#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = "ui.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    fabric8UITemplate(parameters) {
        node(label) {
            body()
        }
    }
}
