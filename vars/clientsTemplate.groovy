#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def defaultLabel = "clients.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)
    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:latest')
    def inheritFrom = parameters.get('inheritFrom', 'base')

      podTemplate(label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
              containers: [
                            [name: 'client', image: "${clientsImage}", command: 'cat', ttyEnabled: true,
                                envVars: [[key: 'KUBERNETES_MASTER', value: 'kubernetes.default']]],
                            [name: 'jnlp', image: 'iocanel/jenkins-jnlp-client:latest', command:'/usr/local/bin/start.sh', args: '${computer.jnlpmac} ${computer.name}', ttyEnabled: false]],
              volumes: [
                      secretVolume(secretName: 'jenkins-docker-cfg', mountPath: 'home/jenkins/.docker'),
                      hostPathVolume(hostPath: 'var/run/docker.sock', mountPath: 'var/run/docker.sock')],
              envVars: [[key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'], [key: 'DOCKER_CONFIG', value: '/home/jenkins/.docker/']]) {
        node(label) {
            body()
        }
    }
}
