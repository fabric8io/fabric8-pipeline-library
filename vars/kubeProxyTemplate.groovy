#!/usr/bin/groovy

def call(Map parameters = [:], body) {
    def defaultLabel = "kubeproxyImage.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def label = parameters.get('label', defaultLabel)
    def inheritFrom = parameters.get('inheritFrom', 'base')
    def kubectlProxyImage = parameters.get('kubeproxyImage', 'gcr.io/google_containers/kubectl:v0.18.0-350-gfb3305edcf6c1a')
    
    podTemplate(label: label, inheritFrom: "${inheritFrom}",
            containers: [
                    [name: 'kubeproxy', image: "${kubectlProxyImage}", args: 'proxy', ttyEnabled: true]]) {
          body(
          )
      }
}
