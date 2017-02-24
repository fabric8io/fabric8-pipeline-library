#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.delegate = config
    body()
    def generateResults = config.generateResults ?: true
    def testDirectory = config.testDirectory ?: "**/target/failsafe-reports/TEST-*.xml";
    if (generateResults) {
        step([$class: 'JUnitResultArchiver', testResults: "${testDirectory}"])
    }

}
