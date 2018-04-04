#!/usr/bin/groovy
import io.fabric8.Fabric8Commands

def call(Map parameters = [:], body) {

    def flow = new Fabric8Commands()

    def defaultLabel = buildId('s2i')
    def label = parameters.get('label', defaultLabel)

    def s2iImage = parameters.get('s2iImage', 'fabric8/s2i-builder:0.0.3')

    def inheritFrom = parameters.get('inheritFrom', 'base')
    def jnlpImage = (flow.isOpenShift()) ? 'fabric8/jenkins-slave-base-centos7:v54e55b7' : 'jenkinsci/jnlp-slave:2.62'

    def cloud = flow.getCloudConfig()

    def utils = new io.fabric8.Utils()
    // 0.13 introduces a breaking change when defining pod env vars so check version before creating build pod
    if (utils.isKubernetesPluginVersion013()) {
        podTemplate(cloud: cloud, label: label, inheritFrom: "${inheritFrom}",
                containers: [
                        containerTemplate(
                                name: 's2i',
                                image: "${s2iImage}",
                                command: '/bin/sh -c',
                                args: 'cat',
                                ttyEnabled: true,
                                workingDir: '/home/jenkins/',
                                envVars: [
                                        envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/')]
                        )
                ],
                volumes: [
                        secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                        hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock'),
                        secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                        secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                        secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git')]) {
            body()
        }
    } else {
        podTemplate(cloud: cloud, label: label, inheritFrom: "${inheritFrom}",
                containers: [

                        [name: 's2i', image: "${s2iImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,  workingDir: '/home/jenkins/',
                         envVars: [[key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]]],
                volumes: [
                        secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                        hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock'),
                        secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                        secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                        secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git')]) {
            body()
        }
    }
}
