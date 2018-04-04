#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('clients')
    def label = parameters.get('label', defaultLabel)

    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:v703b6d9')
    def inheritFrom = parameters.get('inheritFrom', 'base')
    def jnlpImage = (flow.isOpenShift()) ? 'fabric8/jenkins-slave-base-centos7:0.0.1' : 'jenkinsci/jnlp-slave:2.62'

    def cloud = flow.getCloudConfig()

    def utils = new io.fabric8.Utils()

        if (utils.isUseOpenShiftS2IForBuilds()) {
            echo 'Running on openshift so using S2I binary source and Docker strategy'
            podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                    containers: [
                            containerTemplate(
                                    name: 'jnlp',
                                    image: "${jnlpImage}",
                                    args: '${computer.jnlpmac} ${computer.name}',
                                    workingDir: '/home/jenkins/',
                                    resourceLimitMemory: '512Mi'),
                            containerTemplate(
                                    name: 'clients',
                                    image: "${clientsImage}",
                                    command: '/bin/sh -c',
                                    args: 'cat',
                                    ttyEnabled: true,
                                    workingDir: '/home/jenkins/',
                                    envVars: [
                                            envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/')],
                                    resourceLimitMemory: '512Mi')],
                    volumes: [
                            secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                            secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken')]) {
                body()
            }
        } else {
            echo 'Mounting docker socket to build docker images'
            podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                    containers: [
                            containerTemplate(
                                    name: 'clients',
                                    image: "${clientsImage}",
                                    command: '/bin/sh -c',
                                    args: 'cat',
                                    privileged: true,
                                    workingDir: '/home/jenkins/',
                                    ttyEnabled: true,
                                    envVars: [
                                            envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/'),
                                            envVar(key: 'DOCKER_API_VERSION', value: '1.23'),
                                            envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/')]
                            )],
                    volumes: [
                            secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                            secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                            secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                            secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                            hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')]) {
                body()
            }
        }
}
