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

    def dockerfileName = config.dockerfileName ?: 'Dockerfile'
    def containerName = config.containerName ?: 'clients'
    def autoMerge = config.autoMerge ?: false
    def project = config.project
    
    if (!project){
        error 'no project defined'
    }
    def items = project.split('/')
    def repo = items[1]
    def id

    stage "Updating ${project}"
    sh "rm -rf ${repo}"
    sh "git clone https://github.com/${project}.git"
    dir("${repo}"){

        sh "git remote set-url origin git@github.com:${project}.git"

        def uid = UUID.randomUUID().toString()
        sh "git checkout -b versionUpdate${uid}"

        def file = readFile file: "${dockerfileName}"

        flow.updateDockerfileEnvVar("${dockerfileName}", config.propertyName, config.version)

        container(name: containerName) {

            sh 'chmod 600 /root/.ssh-git/ssh-key'
            sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
            sh 'chmod 700 /root/.ssh-git'

            sh "git config --global user.email fabric8-admin@googlegroups.com"
            sh "git config --global user.name fabric8-release"

            def message = "fix(version): update ${dockerfileName} ${config.propertyName} to ${config.version}"
            sh "git add ${dockerfileName}"

            sh "git commit -m \"${message}\""

            sh "git push origin versionUpdate${uid}"

            id = flow.createPullRequest("${message}", "${project}", "versionUpdate${uid}")

            sleep 5 // give a bit of time for GitHub to get itself in order after the new PR
            if (autoMerge){
                flow.mergePR(project, id)
                sh "git push origin --delete versionUpdate${uid}"
            }
        }
    }
}