#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = "release.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    releaseTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
