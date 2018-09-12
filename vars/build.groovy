#!/usr/bin/groovy
import io.openshift.Events
import io.openshift.Utils

def call(Map args) {
    stage("Build application") {
        Events.emit("build.start")
        def namespace = args.namespace ?: Utils.usersNamespace()
        def image = config.runtime() ?: 'oc'

        def status = ""
        try {
          spawn(image: image, version: config.version(), commands: args.commands) {
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
    def isFound = Utils.shWithOutput(this, "oc get is/$isName -n $namespace --ignore-not-found")
    if (!isFound) {
        Utils.ocApply(this, imageStream, namespace)
    } else {
        echo "image stream exist ${isName}"
    }
}

def buildProject(buildConfig, namespace) {
    Utils.ocApply(this, buildConfig, namespace)
    openshiftBuild(buildConfig: "${buildConfig.metadata.name}", showBuildLogs: 'true')
}
