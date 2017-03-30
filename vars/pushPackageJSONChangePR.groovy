#!/usr/bin/groovy
import java.util.LinkedHashMap

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurperClassic


def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()

    def packageJSON = config.parentPaLocation ?: 'package.json'
    def containerName = config.containerName ?: 'clients'
    def autoMerge = config.autoMerge ?: false

    for (int i = 0; i < config.projects.size(); i++) {
        def project = config.projects[i]
        def items = project.split('/')
        def repo = items[1]
        def id

        stage "Updating ${project}"
        sh "rm -rf ${repo}"
        sh "git clone https://github.com/${project}.git"
        sh "cd ${repo} && git remote set-url origin git@github.com:${project}.git"

        def uid = UUID.randomUUID().toString()
        sh "cd ${repo} && git checkout -b versionUpdate${uid}"

        def json = readFile file: "${repo}/${packageJSON}"
        if (shouldWeUpdate(json, config.propertyName, config.version)) {
            // use SED to avoid formatting issues when using JSONBuilder
            flow.updatePackageJSONVersion("${repo}/${packageJSON}", config.propertyName, config.version)

            container(name: containerName) {

                sh 'chmod 600 /root/.ssh-git/ssh-key'
                sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
                sh 'chmod 700 /root/.ssh-git'

                sh "git config --global user.email fabric8-admin@googlegroups.com"
                sh "git config --global user.name fabric8-release"

                def message = "fix(version): update package.json ${config.propertyName} to ${config.version}"
                sh "cd ${repo} && git add ${packageJSON}"

                sh "cd ${repo} && git commit -m \"${message}\""

                sh "cd ${repo} && git push origin versionUpdate${uid}"

                id = flow.createPullRequest("${message}", "${project}", "versionUpdate${uid}")

                if (autoMerge){
                    flow.mergePR(project, id)
                }
            }

            // I dont think we need to wait at the moment, lets just raise all teh PRs
            // waitUntilPullRequestMerged {
            //     name = project
            //     prId = id
            // }

        } else {
            echo "Skippping ${project} as ${config.propertyName} already on version ${config.version}"
        }

    }
}

@NonCPS
def shouldWeUpdate(json, p, v) {
    LinkedHashMap rs = new JsonSlurperClassic().parseText(json)

    if (rs.dependencies[p] == null) {
        error "no property ${p} found in package.json"
    }

    if (rs.dependencies[p].value == null || rs.dependencies[p].value.toString() != v) {
        return true
    } else {
        return false
    }
}