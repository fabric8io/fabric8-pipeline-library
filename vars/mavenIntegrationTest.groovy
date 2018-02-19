#!/usr/bin/groovy
import io.fabric8.Utils

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def utils = new Utils()
    def envName = config.environment
    def kubeNS = "-Dfabric8.environment=${envName}"
    if (envName) {
        // lets try find the actual kubernetes namespace
        try {
            def ns = utils.environmentNamespace(envName)
            if (ns) {
                kubeNS = "-Dnamespace.use.existing=${ns}"
                echo "Running the integration tests in the namespace: ${ns}"
            }
        } catch (e) {
            echo "ERROR: failed to find the environment namespace for ${envName} due to ${e}"
            e.printStackTrace()
        }
    }

    if (utils.isDisabledITests()) {
        echo "WARNING: Integration tests are current DISABLED for these pipelines!"

    } else {
        sh "mvn org.apache.maven.plugins:maven-failsafe-plugin:integration-test ${kubeNS} -P openshift-it -Dit.test=${config.itestPattern} -DfailIfNoTests=${config.failIfNoTests} org.apache.maven.plugins:maven-failsafe-plugin:verify"

        junitResults(body);
    }
  }
