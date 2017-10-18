#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = buildId('deploy-remote')
    def label = parameters.get('label', defaultLabel)

    deployRemoteClusterTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
