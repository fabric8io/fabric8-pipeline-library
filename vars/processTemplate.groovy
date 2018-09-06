#!/usr/bin/groovy

def call(Map params, String yamlFile = ".openshiftio/application.yaml") {
    if (!fileExists(yamlFile)) {
        println("File not found: ${yamlFile}")
        currentBuild.result = 'FAILURE'
        return
    }

    def args = [:]
    params.each{k, v -> args[k.toUpperCase()] = v }

    def templateParams = prepareTemplateParams(args)
    def templateParamString = toParamString(templateParams)

    def templateString = shWithOutput("oc process -f .openshiftio/application.yaml ${templateParamString} -o yaml")

    def templateResources = parseTemplateResources(templateString)
    templateResources["tag"] = args["RELEASE_VERSION"]

    return templateResources
}

def prepareTemplateParams(templateConfig) {
    def templateParams = templateConfig ?: [:]
    templateParams["SUFFIX_NAME"] = templateParams["SUFFIX_NAME"] ?: "-${env.BRANCH_NAME}".toLowerCase()
    templateParams["SOURCE_REPOSITORY_URL"] = templateParams["SOURCE_REPOSITORY_URL"] ?: shWithOutput("git config remote.origin.url")
    templateParams["SOURCE_REPOSITORY_REF"] = templateParams["SOURCE_REPOSITORY_REF"] ?: shWithOutput("git rev-parse --short HEAD")
    templateParams["RELEASE_VERSION"] = templateParams["RELEASE_VERSION"] ?: shWithOutput("git rev-list --count HEAD")
    return templateParams
}

def toParamString(Map templateVars) {
    String parameters = ""
    templateVars.each{ v, k -> parameters = parameters + (v + "=" + k + " ")}
    return parameters.trim()
}

def parseTemplateResources(String template) {
    def resources = [:]
    readYaml(text: template).items.each {
        r -> resources[r.kind] = r
    }
    return resources
}

def shWithOutput(String command) {
    return sh(
        script: command,
        returnStdout: true
    ).trim()
}
