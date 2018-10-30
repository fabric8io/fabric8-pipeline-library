#!/usr/bin/groovy

def call(Map parameters = [:], body) {

    def defaultLabel = buildId('maven')
    def label = parameters.get('label', defaultLabel)

    def nodesDir = "/var/lib/jenkins/nodes"
    def file = new File(nodesDir)
    
    if(file.isDirectory()){
        echo "$nodesDir exists and it's a directory"
    } else {
        error """
        Directory $nodesDir does not exist. /nodes directory stores data for managing slave nodes.
        Without this directory we can't start slave nodes, thus this stops us from finishing the build process.
        """
    }
    
    mavenTemplate(parameters) {
        node(label) {
            body()
        }
    }
}
