#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {

    def defaultLabel = "clients.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:0.6')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    def flow = new Fabric8Commands()
    def cloud = flow.getCloudConfig()

    if (flow.isOpenShift()) {
        echo 'Runnning on openshift so using S2I binary source and Docker strategy'
        podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                containers: [[name: 'clients', image: "${clientsImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true, envVars: [[key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]]],
                volumes: [
                        secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                        secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken')]) {
            body()
        }
    } else {
        echo 'Mounting docker socket to build docker images'
        podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                containers: [[name: 'clients', image: "${clientsImage}", command: '/bin/sh -c', args: 'cat', privileged: true, ttyEnabled: true, envVars: [[key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]]],
                volumes: [
                        secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                        secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                        hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
                envVars: [[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]) {
            body()
        }
    }

}
