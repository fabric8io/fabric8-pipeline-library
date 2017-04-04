#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = buildId('s2i')
    def label = parameters.get('label', defaultLabel)

    s2iTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
