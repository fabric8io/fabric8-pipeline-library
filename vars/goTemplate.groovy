#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = "go.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    def goImage = parameters.get('goImage', 'fabric8/go-builder:1.0.1')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    dockerTemplate {
        podTemplate(label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                containers: [[name: 'go', image: "${goImage}", command: 'cat', ttyEnabled: true, envVars: [
                        [key: 'GOPATH', value: '/home/jenkins/go']]]]) {
            node(label) {
                body()
            }
        }
    }
}
