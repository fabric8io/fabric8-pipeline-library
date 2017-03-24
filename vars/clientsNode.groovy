#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('clients')
    def label = parameters.get('label', defaultLabel)

    clientsTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
