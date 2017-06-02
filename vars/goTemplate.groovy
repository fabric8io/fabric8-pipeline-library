#!/usr/bin/groovy
import io.fabric8.Fabric8Commands

def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()
    def defaultLabel = buildId('go')
    def label = parameters.get('label', defaultLabel)

    def goImage = parameters.get('goImage', 'fabric8/go-builder:1.8.1')
    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:0.9')
    def inheritFrom = parameters.get('inheritFrom', 'base')
    def jnlpImage = (flow.isOpenShift()) ? 'fabric8/jenkins-slave-base-centos7:0.0.1' : 'jenkinsci/jnlp-slave:2.62'

        podTemplate(label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                containers: [
                        //[name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}',  workingDir: '/home/jenkins/'],
                        [name: 'go', image: "${goImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,  workingDir: '/home/jenkins/',
                envVars: [
                        [key: 'GOPATH', value: '/home/jenkins/go']
                ]],
                             [name: 'clients', image: "${clientsImage}", command: 'cat', ttyEnabled: true]],

                volumes:
                        [secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                         secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                         secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git')
                        ]) {

            body()

        }

}
