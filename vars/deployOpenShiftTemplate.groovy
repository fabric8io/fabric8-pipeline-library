#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('clients')
    def label = parameters.get('label', defaultLabel)

    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:0.9')
    def inheritFrom = parameters.get('inheritFrom', 'base')
    def jnlpImage = (flow.isOpenShift()) ? 'fabric8/jenkins-slave-base-centos7:0.0.1' : 'jenkinsci/jnlp-slave:2.62'
    def openshiftConfigSecretName = parameters.get('openshiftConfigSecretName', 'remote-openshift-config')
    def cloud = flow.getCloudConfig()

    if (flow.isOpenShift()) {
        podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                containers: [
                        [name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}',  workingDir: '/home/jenkins/'],
                        [name   : 'clients', image: "${clientsImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,  workingDir: '/home/jenkins/',
                         envVars: [[key: 'TERM', value: 'dumb'],[key: 'KUBECONFIG', value: '/root/home/.oc/cd.conf']]]
                ],
                volumes: [
                        secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
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
                        secretVolume(secretName: openshiftConfigSecretName, mountPath: '/root/home/.oc')
                ]) {
            body()
        }
    }

}
