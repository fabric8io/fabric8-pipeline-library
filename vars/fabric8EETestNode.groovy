#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('test')
    def label = parameters.get('label', defaultLabel)

    fabric8EETestTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
