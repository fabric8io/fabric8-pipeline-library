#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = "clients.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:0.6')
    def mavenImage = parameters.get('mavenImage', 'fabric8/maven-builder:2.2.297')
    def inheritFrom = parameters.get('inheritFrom', 'base')
    podTemplate(label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
            containers: [
                    [name   : 'clients', image: "${clientsImage}", command: 'cat', ttyEnabled: true,
                     envVars: [[key: 'TERM', value: 'dumb']]],
                    [name   : 'maven', image: "${mavenImage}", command: 'cat', ttyEnabled: true,
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
