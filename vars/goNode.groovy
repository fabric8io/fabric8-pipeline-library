#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = buildId('go')
    def label = parameters.get('label', defaultLabel)

    goTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
