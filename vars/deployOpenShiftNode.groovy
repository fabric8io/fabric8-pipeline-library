#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = "deploy-os.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    deployOpenShiftTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
