#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('clients')
    def label = parameters.get('label', defaultLabel)

    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:v703b6d9')
    def inheritFrom = parameters.get('inheritFrom', 'base')
    def containerName = parameters.get('containerName', 'clients')
    def jnlpImage = (flow.isOpenShift()) ? 'fabric8/jenkins-slave-base-centos7:0.0.1' : 'jenkinsci/jnlp-slave:2.62'
    def configSecretName = parameters.get('configSecretName', 'remote-openshift-config')
    def cloud = flow.getCloudConfig()

    def utils = new io.fabric8.Utils()

    if (flow.isOpenShift()) {
        podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                containers: [
                        containerTemplate(
                                name: 'jnlp',
                                image: "${jnlpImage}",
                                args: '${computer.jnlpmac} ${computer.name}',
                                workingDir: '/home/jenkins/'),
                        containerTemplate(
                                name   : "${containerName}",
                                image: "${clientsImage}",
                                command: '/bin/sh -c',
                                args: 'cat',
                                ttyEnabled: true,
                                workingDir: '/home/jenkins/',
                                envVars: [
                                        envVar(key: 'TERM', value: 'dumb'),
                                        envVar(key: 'KUBECONFIG', value: '/root/home/.kube/cd.conf')]
                        )],
                volumes: [
                        secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                        secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                        secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                        secretVolume(secretName: configSecretName, mountPath: '/root/home/.kube')
                ]) {
            body()
        }
    } else {
        podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                containers: [
                        containerTemplate(
                                name   : "${containerName}",
                                image: "${clientsImage}",
                                command: '/bin/sh -c',
                                args: 'cat',
                                ttyEnabled: true,
                                workingDir: '/home/jenkins/',
                                envVars: [
                                        envVar(key: 'TERM', value: 'dumb'),
                                        envVar(key: 'KUBECONFIG', value: '/root/home/.kube/cd.conf')]
                        )],
                volumes: [
                        secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                        secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                        secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                        secretVolume(secretName: configSecretName, mountPath: '/root/home/.kube')
                ]) {
            body()
        }
    }
}
