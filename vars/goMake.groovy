#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()


    def ghOrg =  config.githubOrganisation
    def dockerOrg = config.dockerOrganisation
    def prj = config.project
    def makeCommand = config.makeCommand ?: 'make'

    if (!ghOrg){
        error 'no github organisation defined'
    }
    if (!dockerOrg){
        error 'no docker organisation defined'
    }
    if (!prj){
        error 'no project defined'
    }

    def buildPath = "/home/jenkins/go/src/github.com/${ghOrg}/${prj}"

    sh "mkdir -p ${buildPath}"

    dir(buildPath) {
        checkout scm

        container(name: 'go') {
            stage ('build binary'){
                sh "${makeCommand}"
            }
        }

        if (fileExists('Dockerfile')){
            container(name: 'docker') {

                stage ('build image'){
                    // temporarily disable building docker image until Makefile changes merged in gofabric8
                    //sh "docker build -t ${imageName}:latest ."
                }
            }
        }
    }
  }
