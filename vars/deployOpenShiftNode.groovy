#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = buildId('deploy-os')
    def label = parameters.get('label', defaultLabel)

    deployOpenShiftTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
