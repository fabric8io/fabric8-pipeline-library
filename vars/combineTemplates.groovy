#!/usr/bin/groovy

def call(templates = [], body) {
    if (templates == null || templates.empty) {
        def label = "composite.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
        podTemplate(label: label) {
            node(label) {
                body()
            }
        }
    } else {
        def firstTemplate = templates.remove(0)
        firstTemplate() {
            combineTemplates(templates, body)
        }
    }
}
