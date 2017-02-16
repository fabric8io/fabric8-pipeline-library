#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = "nodejsImage.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)

    def nodejsImage = parameters.get('nodejsImage', 'fabric8/nodejs-builder:0.0.2')

    def inheritFrom = parameters.get('inheritFrom', 'base')


        podTemplate(label: label, inheritFrom: "${inheritFrom}",
                containers: [
                        [name: 'nodejs', image: "${nodejsImage}", command: 'cat', ttyEnabled: true]]
        ) {

            body(

            )
        }


}
