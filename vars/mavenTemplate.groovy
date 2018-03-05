#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
import io.fabric8.Utils

def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()
    def utils = new Utils()

    def defaultLabel = buildId('maven')
    def label = parameters.get('label', defaultLabel)

    def mavenImage = parameters.get('mavenImage', 'fabric8/maven-builder:v9ff62e0')
    def jnlpImage = (flow.isOpenShift()) ? 'fabric8/jenkins-slave-base-centos7:vb0268ae' : 'jenkinsci/jnlp-slave:2.62'
    def inheritFrom = parameters.get('inheritFrom', 'base')


    def cloud = flow.getCloudConfig()

    // 0.13 introduces a breaking change when defining pod env vars so check version before creating build pod
    if (utils.isKubernetesPluginVersion013()) {
        echo "Kubernetes Plugin Version 013"

        def javaOptions = parameters.get('javaOptions', '-Duser.home=/root/ -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -Dsun.zip.disableMemoryMapping=true -XX:+UseParallelGC -XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=10 -XX:GCTimeRatio=4 -XX:AdaptiveSizePolicyWeight=90 -Xms10m -Xmx192m')

        if (utils.isUseOpenShiftS2IForBuilds()) {
            def mavenOpts = parameters.get('mavenOpts', '-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn')

            podTemplate(cloud: cloud, label: label, inheritFrom: "${inheritFrom}", serviceAccount: 'jenkins',
                    containers: [
                            containerTemplate(
                                    name: 'jnlp',
                                    image: "${jnlpImage}",
                                    args: '${computer.jnlpmac} ${computer.name}',
                                    workingDir: '/home/jenkins/',
                                    resourceLimitMemory: '256Mi'),
                            containerTemplate(
                                    name: 'maven',
                                    image: "${mavenImage}",
                                    command: '/bin/sh -c',
                                    args: 'cat',
                                    ttyEnabled: true,
                                    workingDir: '/home/jenkins/',
                                    envVars: [
                                            envVar(key: '_JAVA_OPTIONS', value: javaOptions),
                                            envVar(key: 'MAVEN_OPTS', value: mavenOpts)
                                            ],
                                    resourceLimitMemory: '640Mi')],
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
            echo "building using the docker socket"

            def mavenOpts = parameters.get('mavenOpts', '-Duser.home=/root/ -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn')

            podTemplate(cloud: cloud,
                    label: label,
                    inheritFrom: "${inheritFrom}",
                    containers: [
                            containerTemplate (
                                    //[name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}'],
                                    name: 'maven',
                                    image: "${mavenImage}",
                                    command: '/bin/sh -c',
                                    args: 'cat',
                                    ttyEnabled: true,
                                    alwaysPullImage: false,
                                    workingDir: '/home/jenkins/',
                                    //resourceLimitMemory: '640Mi',
                                    envVars: [
                                            envVar(key: '_JAVA_OPTIONS', value: javaOptions),
                                            envVar(key: 'MAVEN_OPTS', value: mavenOpts),
                                            envVar(key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/')])],
                    volumes: [
                            secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
                            secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                            secretVolume(secretName: 'jenkins-release-gpg', mountPath: '/home/jenkins/.gnupg'),
                            secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                            secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                            secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git'),
                            hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')])
                    {

                        body(

                        )
                    }
        }
    } else {
        if (utils.isUseOpenShiftS2IForBuilds()) {
            def javaOptions = parameters.get('javaOptions', '-Duser.home=/root/ -XX:+UseParallelGC -XX:MinHeapFreeRatio=20 -XX:MaxHeapFreeRatio=40 -XX:GCTimeRatio=4 -XX:AdaptiveSizePolicyWeight=90 -Xmx256m')
            def mavenOpts = parameters.get('mavenOpts', '-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn')

            podTemplate(cloud: cloud, label: label, inheritFrom: "${inheritFrom}", serviceAccount: 'jenkins', restartPolicy: 'OnFailure',
                    containers: [
                            [name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}', workingDir: '/home/jenkins/',
                             resourceLimitMemory: '512Mi'], // needs to be high to work on OSO
                            [name: 'maven', image: "${mavenImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true, workingDir: '/home/jenkins/',
                             envVars: [
                                     [key: '_JAVA_OPTIONS', value: javaOptions],
                                     [key: 'MAVEN_OPTS', value: mavenOpts]
                                     ],
                             resourceLimitMemory: '1024Mi']],
                    volumes: [secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
                              secretVolume(secretName: 'jenkins-release-gpg', mountPath: '/home/jenkins/.gnupg'),
                              secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
                              secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh'),
                              secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git')],
                    envVars: [[key: 'GIT_COMMITTER_EMAIL', value: 'fabric8@googlegroups.com'], [key: 'GIT_COMMITTER_NAME', value: 'fabric8']]) {

                body(

                )
            }
        } else {
            echo "building using the docker socket"

            def mavenOpts = parameters.get('mavenOpts', '-Duser.home=/root/ -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn')

            podTemplate(cloud: cloud, label: label, inheritFrom: "${inheritFrom}",
                    containers: [
                            //[name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}'],
                            [name: 'maven', image: "${mavenImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,
                             envVars: [
                                     [key: 'MAVEN_OPTS', value: mavenOpts]]]],
                    volumes: [secretVolume(secretName: 'jenkins-maven-settings', mountPath: '/root/.m2'),
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
}
