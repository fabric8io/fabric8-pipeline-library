#!/usr/bin/groovy
import io.openshift.Utils

def call(args=[:]) {
    def file = args.file ?: ".openshiftio/application.yaml"
    if (!fileExists(file)) {
        error "Application template could not be found at $file; aborting ..."
        currentBuild.result = 'ABORTED'
        return
    }

    def params = [:]

    // sanitize input params and apply defaults
    def inputs = args.params ?: [:]
    inputs.each{k, v -> params[k.toUpperCase()] = v }
    params = applyDefaults(params)

    def yaml = Utils.shWithOutput(this, "oc process -f $file ${stringizeParams(params)} -o yaml")
    def resources = parseTemplate(yaml)
    resources.tag = params.RELEASE_VERSION

    return resources
}

def applyDefaults(override=[:]) {
    def ret = [:]
    ret["SUFFIX_NAME"] = override["SUFFIX_NAME"] ?: "-${env.BRANCH_NAME}".toLowerCase()
    ret["SOURCE_REPOSITORY_URL"] = override["SOURCE_REPOSITORY_URL"] ?: Utils.shWithOutput(this, "git config remote.origin.url")
    ret["SOURCE_REPOSITORY_REF"] = override["SOURCE_REPOSITORY_REF"] ?: Utils.shWithOutput(this, "git rev-parse --short HEAD")
    ret["RELEASE_VERSION"] = override["RELEASE_VERSION"] ?: Utils.shWithOutput(this, "git rev-list --count HEAD")
    return ret
}

def stringizeParams(Map params) {
    String ret = ""
    params.each{ v, k -> ret = ret + (v + "=" + k + " ")}
    return ret.trim()
}

def parseTemplate(String yaml) {
    def resources = [:]
    readYaml(text: yaml).items.each {
        r -> resources[r.kind] = r
    }
    return resources
}
