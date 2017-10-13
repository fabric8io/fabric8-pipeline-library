#!/usr/bin/groovy

def call(Map parameters = [:], body) {

  def defaultLabel = buildId('test')
  def label = parameters.get('label', defaultLabel)

  def beforeTest = parameters.get('beforeTest', "")
  def afterTest = parameters.get('afterTest', "")

  fabric8EETestTemplate(parameters) {
    node(label) {

      def screenshotsStash = "screenshots"
      try {
        container(name: 'test') {
          try {
            sh """
                ${beforeTest}

                /test/ee_tests/entrypoint.sh

                ${afterTest}
             """
          } finally {

            echo ""
            echo ""
            echo "functional_tests.log:"
            sh "cat /test/ee_tests/functional_tests.log"

            sh "mkdir -p screenshots"
            sh "cp -r /test/ee_tests/target/screenshots/* screenshots"

            stash name: screenshotsStash, includes: "screenshots/*"
          }
        }
      } finally {

        echo "unstashing ${screenshotsStash}"
        unstash screenshotsStash

        echo "now lets archive: ${screenshotsStash}"
        try {
          archiveArtifacts artifacts: 'screenshots/*'
        } catch (e) {
          echo "could not find: screenshots* ${e}"
        }
      }
    }
  }
}
