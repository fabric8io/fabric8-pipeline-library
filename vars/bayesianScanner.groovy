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
                sh "#!/bin/bash \n" +
                        "mvn io.github.stackinfo:stackinfo-maven-plugin:0.2:prepare"
                        "mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:collect -DoutputFile=direct-dependencies.txt -DincludeScope=runtime -DexcludeTransitive=true"
                        "mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:collect -DoutputFile=transitive-dependencies.txt -DincludeScope=runtime -DexcludeTransitive=false"
                retry(3) {
                    def project = flow.getGitHubProject()
                    def response = bayesianAnalysis url: 'https://bayesian-link', gitUrl: "https://github.com/${project}.git"
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
