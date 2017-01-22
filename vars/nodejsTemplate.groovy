#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = "nodejsImage.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    def nodejsImage = parameters.get('nodejsImage', 'fabric8/nodejs-builder:0.0.1')
    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:0.1')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    def flow = new io.fabric8.Fabric8Commands()

    if (flow.isOpenShift()) {
        podTemplate(label: label, inheritFrom: "${inheritFrom}",
                containers: [
                        [name: 'nodejs', image: "${nodejsImage}", command: 'cat', ttyEnabled: true],
                        [name: 'clients', image: "${clientsImage}", command: 'cat', ttyEnabled: true]],
                volumes: [secretVolume(secretName: 'jenkins-release-gpg', mountPath: '/home/jenkins/.gnupg'),
                          secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                          secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                          secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git')]) {

            body(

            )
        }
    } else {
        podTemplate(label: label, inheritFrom: "${inheritFrom}",
                containers: [
                        [name: 'nodejs', image: "${nodejsImage}", command: 'cat', ttyEnabled: true],
                        [name: 'clients', image: "${clientsImage}", command: 'cat', ttyEnabled: true, privileged: true]],
                volumes: [secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                          secretVolume(secretName: 'jenkins-release-gpg', mountPath: '/home/jenkins/.gnupg'),
                          secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                          secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                          secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                          hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
                envVars: [[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]
        ) {

            body(

            )
        }
    }

}
