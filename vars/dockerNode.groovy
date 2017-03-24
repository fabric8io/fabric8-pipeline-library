#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('docker')
    def label = parameters.get('label', defaultLabel)

    dockerTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
