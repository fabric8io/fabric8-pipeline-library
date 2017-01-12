#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = "clients.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:0.1')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    podTemplate(label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
            containers: [[name: 'clients', image: "${clientsImage}", command: 'cat', privileged: true, ttyEnabled: true, envVars: [[key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]]],
            volumes: [
                    secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                    secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                    hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
            envVars: [[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]) {
        node(label) {
            body()
        }
    }
}
