#!/usr/bin/groovy
import io.fabric8.Fabric8Commands

def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('chunky')
    def label = parameters.get('label', defaultLabel)

    def chunkyImage = parameters.get('chunkyImage', 'fabric8/chunky-builder:0.0.2')
    def inheritFrom = parameters.get('inheritFrom', 'base')
    def jnlpImage = (flow.isOpenShift()) ? 'fabric8/jenkins-slave-base-centos7:v54e55b7' : 'jenkinsci/jnlp-slave:2.62'

    def cloud = flow.getCloudConfig()

    def utils = new io.fabric8.Utils()

    if (utils.isUseOpenShiftS2IForBuilds()) {
        podTemplate(cloud: cloud, label: label, inheritFrom: "${inheritFrom}",
                containers: [
                        containerTemplate(
                                name: 'jnlp',
                                image: "${jnlpImage}",
                                args: '${computer.jnlpmac} ${computer.name}',
                                workingDir: '/home/jenkins/'),
                        containerTemplate(
                                name: 'chunky',
                                image: "${chunkyImage}",
                                command: '/bin/sh -c',
                                args: 'cat',
                                ttyEnabled: true,
                                workingDir: '/home/jenkins/',
                                envVars: [
                                        envVar(key: 'MAVEN_OPTS', value: '-Duser.home=/root/')
                                ])],
                volumes: [
                        secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
                        secretVolume(secretName: 'jenkins-release-gpg', mountPath: '/home/jenkins/.gnupg'),
                        secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                        secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                        secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git')]) {

            body(

            )
        }
    } else {
        podTemplate(cloud: cloud, label: label, inheritFrom: "${inheritFrom}",
                containers: [
                        containerTemplate(
                                name: 'chunky',
                                image: "${chunkyImage}",
                                command: '/bin/sh -c',
                                args: 'cat',
                                ttyEnabled: true,
                                envVars: [
                                        envVar(key: 'MAVEN_OPTS', value: '-Duser.home=/root/'),
                                        envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/')
                                ])],
                volumes: [
                        secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
                        secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                        secretVolume(secretName: 'jenkins-release-gpg', mountPath: '/home/jenkins/.gnupg'),
                        secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                        secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                        secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                        hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')]
        ) {

            body(

            )
        }
    }
}
