#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
def call(Map parameters = [:], body) {

    def defaultLabel = "nodejsImage.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    def nodejsImage = parameters.get('nodejsImage', 'fabric8/nodejs-builder:0.0.2')
    def inheritFrom = parameters.get('inheritFrom', 'base')
    def flow = new Fabric8Commands()
    def cloud = flow.getCloudConfig()

        podTemplate(cloud: cloud, label: label, inheritFrom: "${inheritFrom}",
                containers: [
                        [name: 'nodejs', image: "${nodejsImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true]]) {
            body()
        }


}
