#!/usr/bin/groovy

import io.fabric8.Fabric8Commands

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()


    def ghOrg = config.githubOrganisation
    def dockerOrg = config.dockerOrganisation
    def prj = config.project
    def buildOptions = config.dockerBuildOptions ?: ''

    def version

    if (!ghOrg) {
        error 'no github organisation defined'
    }
    if (!dockerOrg) {
        error 'no docker organisation defined'
    }
    if (!prj) {
        error 'no project defined'
    }

    def buildPath = "/home/jenkins/go/src/github.com/${ghOrg}/${prj}"

    sh "mkdir -p ${buildPath}"

    dir(buildPath) {
        git "https://github.com/${ghOrg}/${prj}.git"
        sh "git remote set-url origin git@github.com:${ghOrg}/${prj}.git"

        container(name: 'go') {
            stage('build binary') {

                def flow = new Fabric8Commands()
                flow.setupGitSSH()

                // if you want a nice N.N.N version number then use a VERSION file, if not default to short commit sha
                if (fileExists('version/VERSION')) {
                    sh "gobump -f version/VERSION patch"
                    sh "git commit -am 'Version bump'"
                    version = readFile('version/VERSION').trim()
                    if (!version) {
                        error 'no version found'
                    }
                    sh "git push origin master"
                } else {
                    version = getNewVersion {}
                }
                String ghToken = readFile '/home/jenkins/.apitoken/hub'
                wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [
                        [password: ghToken, var: 'GH_PASSWORD']]]) {

                    sh "export GITHUB_ACCESS_TOKEN=${ghToken} make -e BRANCH=master release"
                }
            }
        }

        container(name: 'docker') {
            def imageName = "docker.io/${dockerOrg}/${prj}"

            stage('build image') {
                sh "docker build -t ${imageName}:latest ${buildOptions} ."
            }

            stage('push latest images') {
                sh "docker push ${imageName}:latest"
                sh "docker tag ${imageName}:latest ${imageName}:${version}"
                sh "docker push ${imageName}:${version}"
            }
        }
    }
    return version
}
