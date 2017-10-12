#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = buildId('clients')
    def label = parameters.get('label', defaultLabel)

    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:v703b6d9')
    def mavenImage = parameters.get('mavenImage', 'fabric8/maven-builder:v7973e33')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    def cloud = flow.getCloudConfig()

    def utils = new io.fabric8.Utils()
    // 0.13 introduces a breaking change when defining pod env vars so check version before creating build pod
    if (utils.isKubernetesPluginVersion013()) {
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
                                        envVar(key: 'TERM', value: 'dumb')
                                ]),
                        containerTemplate(
                                name   : 'maven',
                                image: "${mavenImage}",
                                command: '/bin/sh -c',
                                args: 'cat',
                                ttyEnabled: true,
                                workingDir: '/home/jenkins/',
                                envVars: [
                                        envVar(key: 'MAVEN_OPTS', value: '-Duser.home=/root/')
                                ])
                ],
                volumes: [
                        secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
                        persistentVolumeClaim(claimName: 'jenkins-mvn-local-repo', mountPath: '/root/.mvnrepository'),
                        secretVolume(secretName: 'gke-service-account', mountPath: '/root/home/.gke')
                ]) {
            body()
        }
    } else {
        podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
                containers: [
                        //[name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}',  workingDir: '/home/jenkins/'],
                        [name   : 'clients', image: "${clientsImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,  workingDir: '/home/jenkins/',
                         envVars: [[key: 'TERM', value: 'dumb']]],
                        [name   : 'maven', image: "${mavenImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,  workingDir: '/home/jenkins/',
                         envVars: [
                                 [key: 'MAVEN_OPTS', value: '-Duser.home=/root/']]]
                ],
                volumes: [
                        secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
                        persistentVolumeClaim(claimName: 'jenkins-mvn-local-repo', mountPath: '/root/.mvnrepository'),
                        secretVolume(secretName: 'gke-service-account', mountPath: '/root/home/.gke')
                ]) {
            body()
        }
    }
}
