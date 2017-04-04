#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('chunky')
    def label = parameters.get('label', defaultLabel)

    chunkyTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
