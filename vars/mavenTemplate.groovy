#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
import io.fabric8.Utils

def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()
    def utils = new Utils()

    def defaultLabel = buildId('maven')
    def label = parameters.get('label', defaultLabel)

    def mavenImage = parameters.get('mavenImage', 'fabric8/maven-builder:2.2.297')
    def jnlpImage = (flow.isOpenShift()) ? 'fabric8/jenkins-slave-base-centos7:0.0.1' : 'jenkinsci/jnlp-slave:2.62'
    def inheritFrom = parameters.get('inheritFrom', 'base')

    def cloud = flow.getCloudConfig()

    if (flow.isOpenShift() && !utils.isUseDockerSocket()) {
        podTemplate(cloud: cloud, label: label, inheritFrom: "${inheritFrom}", serviceAccount: 'jenkins', restartPolicy: 'OnFailure',
                containers: [
                        [name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}', workingDir: '/home/jenkins/',
                        resourceLimitMemory: '512Mi'], // needs to be high to work on OSO
                        [name: 'maven', image: "${mavenImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true, workingDir: '/home/jenkins/',
                         envVars: [
                                 [key: 'MAVEN_OPTS', value: '-Duser.home=/root/ -XX:+UseParallelGC \
         -XX:MinHeapFreeRatio=20 \
         -XX:MaxHeapFreeRatio=40 \
         -XX:GCTimeRatio=4 \
         -XX:AdaptiveSizePolicyWeight=90 \
         -Xms512m -Xmx512m']],
                         resourceLimitMemory: '768Mi']],
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
                        //[name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}'],
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
