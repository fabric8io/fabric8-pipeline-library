#!/usr/bin/groovy
import io.fabric8.Fabric8Commands

def call(Map parameters = [:], body) {

    def defaultLabel = "maven.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    def mavenImage = parameters.get('mavenImage', 'fabric8/maven-builder:2.2.297')
    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:0.6')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    def flow = new Fabric8Commands()
    def cloud = flow.getCloudConfig()

    if (flow.isOpenShift()) {
        podTemplate(cloud: cloud, label: label, inheritFrom: "${inheritFrom}",
                containers: [
                        [name: 'jnlp', image: 'jenkinsci/jnlp-slave:2.62', args: '${computer.jnlpmac} ${computer.name}'],
                        [name: 'maven', image: "${mavenImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,
                         envVars: [
                                 [key: 'MAVEN_OPTS', value: '-Duser.home=/root/']]]],
                volumes: [secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
                          persistentVolumeClaim(claimName: 'jenkins-mvn-local-repo', mountPath: '/root/.mvnrepository'),
                          secretVolume(secretName: 'jenkins-release-gpg', mountPath: '/home/jenkins/.gnupg'),
                          secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                          secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                          secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git')],
                envVars: [[key: 'GIT_COMMITTER_EMAIL', value: 'fabric8@googlegroups.com'], [key: 'GIT_COMMITTER_NAME', value: 'fabric8']]) {

            body(

            )
        }
    } else {
        podTemplate(cloud: cloud, label: label, inheritFrom: "${inheritFrom}",
                containers: [
                        [name: 'jnlp', image: 'jenkinsci/jnlp-slave:2.62', args: '${computer.jnlpmac} ${computer.name}'],
                        [name: 'maven', image: "${mavenImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,
                         envVars: [
                                 [key: 'MAVEN_OPTS', value: '-Duser.home=/root/']]]],
                volumes: [secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
                          persistentVolumeClaim(claimName: 'jenkins-mvn-local-repo', mountPath: '/root/.mvnrepository'),
                          secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                          secretVolume(secretName: 'jenkins-release-gpg', mountPath: '/home/jenkins/.gnupg'),
                          secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                          secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                          secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                          hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
                envVars: [[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]
        ) {

            body(

            )
        }
    }

}
