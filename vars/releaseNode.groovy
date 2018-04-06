#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = buildId('release')
    def label = parameters.get('label', defaultLabel)

    releaseTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
