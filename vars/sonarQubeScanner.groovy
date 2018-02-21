#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.delegate = config
    body()
    def serviceName = config.serviceName ?: "sonarqube"
    def port = config.servicePort ?: "9000"
    def scannerVersion = config.scannerVersion ?: "2.8"
    def runSonarScanner = config.runSonarScanner ?: "true"


    if (runSonarScanner) {
        def flow = new io.fabric8.Fabric8Commands()
        echo "Checking ${serviceName} exists"
        if (flow.hasService(serviceName)) {
            try {
                def srcDirectory = pwd()
                def tmpDir = pwd(tmp: true)

                //work in tmpDir - as sonar scanner will download files from the server
                dir(tmpDir) {

                    def localScanner = "scanner-cli.jar"

                    def scannerURL = "http://central.maven.org/maven2/org/sonarsource/scanner/cli/sonar-scanner-cli/${scannerVersion}/sonar-scanner-cli-${scannerVersion}.jar"

                    echo "downloading scanner-cli"

                    sh "curl -o ${localScanner}  ${scannerURL} "

                    echo("executing sonar scanner ")

                    sh "java -jar ${localScanner}  -Dsonar.host.url=http://${serviceName}:${port}  -Dsonar.projectKey=${env.JOB_NAME} -Dsonar.sources=${srcDirectory}"
                }

            } catch (err) {
                echo "Failed to execute scanner:"
                echo "Exception: ${err}"
                throw err
            }

        } else {
            echo "Code validation service: ${serviceName} not available"
        }
    }

  }
