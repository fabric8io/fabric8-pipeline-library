#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('deploy')
    def label = parameters.get('label', defaultLabel)

    deployTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
