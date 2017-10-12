#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('clients')
    def label = parameters.get('label', defaultLabel)

    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:v703b6d9')
    def inheritFrom = parameters.get('inheritFrom', 'base')
    def jnlpImage = (flow.isOpenShift()) ? 'fabric8/jenkins-slave-base-centos7:0.0.1' : 'jenkinsci/jnlp-slave:2.62'
    def openshiftConfigSecretName = parameters.get('openshiftConfigSecretName', 'remote-openshift-config')
    def cloud = flow.getCloudConfig()

    def utils = new io.fabric8.Utils()
    // 0.13 introduces a breaking change when defining pod env vars so check version before creating build pod
    if (utils.isKubernetesPluginVersion013()) {
        if (flow.isOpenShift()) {
            podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                    containers: [
                            containerTemplate(
                                    name: 'jnlp',
                                    image: "${jnlpImage}",
                                    args: '${computer.jnlpmac} ${computer.name}',
                                    workingDir: '/home/jenkins/'),
                            containerTemplate(
                                    name   : 'clients',
                                    image: "${clientsImage}",
                                    command: '/bin/sh -c',
                                    args: 'cat',
                                    ttyEnabled: true,
                                    workingDir: '/home/jenkins/',
                                    envVars: [
                                            envVar(key: 'TERM', value: 'dumb'),
                                            envVar(key: 'KUBECONFIG', value: '/root/home/.oc/cd.conf')]
                            )],
                    volumes: [
                            secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                            secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                            secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                            secretVolume(secretName: openshiftConfigSecretName, mountPath: '/root/home/.oc')
                    ]) {
                body()
            }
        } else {
            podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                    containers: [
                            containerTemplate(
                                    name   : 'clients',
                                    image: "${clientsImage}",
                                    command: '/bin/sh -c',
                                    args: 'cat',
                                    ttyEnabled: true,
                                    workingDir: '/home/jenkins/',
                                    envVars: [
                                            envVar(key: 'TERM', value: 'dumb'),
                                            envVar(key: 'KUBECONFIG', value: '/root/home/.oc/cd.conf')]
                            )],
                    volumes: [
                            secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                            secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                            secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                            secretVolume(secretName: openshiftConfigSecretName, mountPath: '/root/home/.oc')
                    ]) {
                body()
            }
        }
    } else {
        if (flow.isOpenShift()) {
            podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                    containers: [
                            [name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}',  workingDir: '/home/jenkins/'],
                            [name   : 'clients', image: "${clientsImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,  workingDir: '/home/jenkins/',
                             envVars: [[key: 'TERM', value: 'dumb'],[key: 'KUBECONFIG', value: '/root/home/.oc/cd.conf']]]
                    ],
                    volumes: [
                            secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                            secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                            secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                            secretVolume(secretName: openshiftConfigSecretName, mountPath: '/root/home/.oc')
                    ]) {
                body()
            }
        } else {
            podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                    containers: [
                            [name   : 'clients', image: "${clientsImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,  workingDir: '/home/jenkins/',
                             envVars: [[key: 'TERM', value: 'dumb'],[key: 'KUBECONFIG', value: '/root/home/.oc/cd.conf']]]
                    ],
                    volumes: [
                            secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                            secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                            secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                            secretVolume(secretName: openshiftConfigSecretName, mountPath: '/root/home/.oc')
                    ]) {
                body()
            }
        }
    }
}
