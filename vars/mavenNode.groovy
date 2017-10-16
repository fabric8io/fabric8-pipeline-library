#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = buildId('maven')
    def label = parameters.get('label', defaultLabel)

    mavenTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
