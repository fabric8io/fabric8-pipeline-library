#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.delegate = config
    body()
    def archiveTestResults = config.archiveTestResults ?: true

    if (archiveTestResults) {
        def surefire = new File ("target/surefire-reports")
        if (surefire.exists() && surefire.isDirectory()) {
            step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
        }

        def failsafe = new File ("target/failsafe-reports")
        if (failsafe.exists() && failsafe.isDirectory()) {
            step([$class: 'JUnitResultArchiver', testResults: '**/target/failsafe-reports/TEST-*.xml'])
        }
    }

}
