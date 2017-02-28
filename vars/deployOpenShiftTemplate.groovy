#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {
    def flow = new Fabric8Commands()

    def defaultLabel = "clients.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    def clientsImage = parameters.get('clientsImage', 'fabric8/builder-clients:0.6')
    def inheritFrom = parameters.get('inheritFrom', 'base')

    def cloud = flow.getCloudConfig()

    podTemplate(cloud: cloud, label: label, serviceAccount: 'jenkins', inheritFrom: "${inheritFrom}",
            containers: [
                    [name   : 'clients', image: "${clientsImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true,  workingDir: '/home/jenkins/',
                     envVars: [[key: 'TERM', value: 'dumb']]]
            ],
            volumes: [
                    secretVolume(secretName: 'remote-openshift-token', mountPath: '/root/home/.oc')
            ]) {
        body()
    }
}
