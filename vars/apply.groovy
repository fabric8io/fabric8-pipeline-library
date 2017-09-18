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

    kubernetesApply(environment: environment)

    def utils = new Utils()
    if (!utils.isUseOpenShiftS2IForBuilds()) {
        def data = utils.getConfigMap(null, 'exposecontroller', 'config.yml')
        def configYAML = utils.parseConfigMapData(data)
        def http = configYAML['http']
        def exposer = configYAML['exposer']
        def domain = configYAML['domain']

        if (!http){
            error "no value for key http found in exposecontroller configmap"
        }
        if (!exposer){
            error "no value for key exposer found in exposecontroller configmap"
        }
        if (!domain){
            error "no value for key domain found in exposecontroller configmap"
        }

        sh "exposecontroller --watch-namespace ${environment} --http ${http} --exposer ${exposer} --domain ${domain}"
    }
}