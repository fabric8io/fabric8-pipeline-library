#!/usr/bin/groovy
import io.openshift.Utils;

def call(Map args = [:]) {
    def userNamespace = Utils.usersNamespace();
    def deployNamespace = userNamespace + "-" + args.env;

    if (args.approval == 'manual') {
        askForInput(args.app.tag, args.env, args.timeout?: 30)
    }

    stage ("Deploy to ${args.env}") {
      spawn(image: "oc") {
        tagImageToDeployEnv(deployNamespace, userNamespace, args.app.ImageStream, args.app.tag)
        def routeUrl = deployEnvironment(deployNamespace, args.app.DeploymentConfig, args.app.Service, args.app.Route)
        displayRouteURLOnUI(deployNamespace, args.env, routeUrl, args.app.Route, args.app.tag)
      }
    }
}

def askForInput(String version, String environment, int duration) {
    def appVersion = version ? "version ${version}" : "application"
    def appEnvironment = environment ? "${environment} environment" : "next environment"
    def proceedMessage = """Would you like to promote ${appVersion} to the ${appEnvironment}?"""

    stage("Approve") {
        try {
            timeout(time: duration, unit: 'MINUTES') {
                input id: 'Proceed', message: "\n${proceedMessage}"
            }
        } catch (err) {
            currentBuild.result = 'ABORTED'
            error "Timeout of $duration minutes has elapsed; aborting ..."
        }
    }
}

def tagImageToDeployEnv(deployNamespace, userNamespace, is, tag) {
    try {
        def imageName = is.metadata.name
        sh "oc tag -n ${deployNamespace} --alias=true ${userNamespace}/${imageName}:${tag} ${imageName}:${tag}"
    } catch (err) {
        error "Error running OpenShift command ${err}"
    }
}

def deployEnvironment(deployNamespace, dc, service, route) {
    Utils.ocApply(this, dc, deployNamespace)
    openshiftVerifyDeployment(depCfg: "${dc.metadata.name}", namespace: "${deployNamespace}")
    Utils.ocApply(this, service, deployNamespace)
    Utils.ocApply(this, route, deployNamespace)
    return displayRouteURL(deployNamespace, route)

}

def displayRouteURLOnUI(namespace, env, routeUrl, route, version) {
   def routeMetadata = """---
environmentName: "$env"
serviceUrls:
  $route.metadata.name: "$routeUrl"
deploymentVersions:
  $route.metadata.name: "$version"
"""
    Utils.addAnnotationToBuild(this, "environment.services.fabric8.io/$namespace", routeMetadata);
}

def displayRouteURL(namespace, route) {
    try {
        def routeUrl = Utils.shWithOutput(this, "oc get route -n ${namespace} ${route.metadata.name} --template 'http://{{.spec.host}}'")
        echo namespace.capitalize() + " URL: ${routeUrl}"
        return routeUrl
    } catch (err) {
        error "Error running OpenShift command ${err}"
    }
    return null
}
