#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = "go.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    def goImage = parameters.get('goImage', 'fabric8/go-builder:1.0.8')
    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:latest')
    def inheritFrom = parameters.get('inheritFrom', 'base')

        podTemplate(label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                containers: [[name: 'go', image: "${goImage}", command: 'cat', ttyEnabled: true,
                envVars: [
                        [key: 'GOPATH', value: '/home/jenkins/go']
                ]],
                             [name: 'clients', image: "${clientsImage}", command: 'cat', ttyEnabled: true]],

                volumes:
                        [secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                         secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                         secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git')
                        ]) {
            node(label) {
                body()
            }
        }

}