#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = "docker.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    def goImage = parameters.get('goImage', 'fabric8/go-builder:1.0.0')
    def inheritFrom = parameters.get('inheritFrom', 'base')

      podTemplate(label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
            containers: [[name: 'go', image: "${goImage}", command: 'cat', ttyEnabled: true]],
            volumes: [
                              secretVolume(secretName: 'jenkins-docker-cfg', mountPath: '/home/jenkins/.docker'),
                              hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')],
            envVars: [[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]) {
        node(label) {
            body()
        }
    }
}

// def call(body) {

//     def label = "buildpod.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
//       podTemplate(label: label, serviceAccount: 'jenkins', containers: [
//             [name: 'go', image: 'fabric8/go-builder:1.0.0', command: 'cat', ttyEnabled: true, envVars: [
//                     [key: 'DOCKER_CONFIG', value: '/root/.docker/'],
//                     [key: 'KUBERNETES_MASTER', value: 'kubernetes.default']]],
//             [name: 'jnlp', image: 'iocanel/jenkins-jnlp-client:latest', command:'/usr/local/bin/start.sh', args: '${computer.jnlpmac} ${computer.name}', ttyEnabled: false,
//                     envVars: [
//                         [key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'],
//                         [key: 'DOCKER_CONFIG', value: '/root/.docker/']
//                         ]]],
//             volumes: [
//                     [$class: 'SecretVolume', mountPath: '/root/.docker', secretName: 'jenkins-docker-cfg'],
//                     [$class: 'HostPathVolume', mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock']
//             ]) {
//         node(label) {
//             body()
//         }
//     }
// }
