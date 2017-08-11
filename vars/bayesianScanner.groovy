#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.delegate = config
    body()
    def serviceName = config.serviceName ?: "bayesian-link";
    def runBayesianScanner = config.runBayesianScanner ?: "true"

    if (runBayesianScanner) {
        def flow = new io.fabric8.Fabric8Commands()
        def utils = new io.fabric8.Utils()
        echo "Checking ${serviceName} exists"
        if (flow.hasService(serviceName)) {
            try {
                sh 'mvn io.github.stackinfo:stackinfo-maven-plugin:0.2:prepare'
                retry (3){
                    def response = bayesianAnalysis url: 'https://bayesian-link'
                    if (response.success) {
                        utils.addAnnotationToBuild('fabric8.io/bayesian.analysisUrl', response.getAnalysisUrl())
                    } else {
                        error "Bayesian analysis failed ${response}"
                    }
                }
            } catch (err) {
                echo "Unable to run Bayesian analysis: ${err}"
            }

        } else {
            echo "Code validation service: ${serviceName} not available"
        }
    }
}
