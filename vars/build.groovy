#!/usr/bin/groovy
import io.fabric8.Events
import io.fabric8.Utils
import groovy.json.*


def call(Map args) {
    stage("Build application") {
        Events.emit("build.start")
        def namespace = args.namespace ?: new Utils().getUsersNamespace()
        def image = config.runtime() ?: 'oc'

        def status = ""
        try {
          spawn(image: image) {
            createImageStream(args.app.ImageStream, namespace)
            buildProject(args.app.BuildConfig, namespace)
          }
          status = "pass"
        } catch (e) {
            status = "fail"
            echo "build failed"
            throw e
        } finally {
          Events.emit(["build.end", "build.${status}"], [status: status, namespace: namespace])
        }
    }
}

def createImageStream(imageStream, namespace) {
    def isName = imageStream.metadata.name
    def isFound = shWithOutput("oc get is/$isName -n $namespace --ignore-not-found")
    if (!isFound) {
        ocApplyResource(imageStream, namespace)
    } else {
        echo "image stream exist ${isName}"
    }
}

def buildProject(buildConfig, namespace) {
    ocApplyResource(buildConfig, namespace)
    openshiftBuild(buildConfig: "${buildConfig.metadata.name}", showBuildLogs: 'true')
}

def shWithOutput(String command) {
    return sh(
            script: command,
            returnStdout: true
    ).trim()
}

def ocApplyResource(resource, namespace) {
    def resourceFile = ".openshiftio/.tmp-${namespace}-${env.BUILD_NUMBER}-${resource.kind.toLowerCase()}.yaml"
    writeYaml file: resourceFile, data: resource
    sh """
        oc apply -f ${resourceFile} -n ${namespace}
        rm ${resourceFile}
    """
}
