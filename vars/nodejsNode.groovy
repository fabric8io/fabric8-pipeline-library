#!/usr/bin/groovy

def call(Map parameters = [:], Closure body) {

    def defaultLabel = buildId('nodejs')
    def label = parameters.get('label', defaultLabel)
    def config = [templateConfig: "nodejsnode"]

    node {
        checkout scm

        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.delegate = config
        body()

        echo "nodejsnode config ${config}"
    }

}
