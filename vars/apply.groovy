#!/usr/bin/groovy
import io.fabric8.Utils
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def environment = config.environment
    if (!environment){
        error 'no environment specified'
    }

    // TODO lets add a default timeout loaded from the fabric8-pipelines ConfigMap
    kubernetesApply(environment: environment)
}