#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = "docker.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    def goImage = parameters.get('goImage', 'fabric8/go-builder:1.0.0')
    def inheritFrom = parameters.get('inheritFrom', 'base')

      podTemplate(label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
            containers: [[name: 'go', image: "${goImage}", command: 'cat', ttyEnabled: true]],
            volumes: [
                              secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                              hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
            envVars: [[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]) {
        node(label) {
            body()
        }
    }
}
