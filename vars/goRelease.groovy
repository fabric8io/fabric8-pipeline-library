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
        git "https://github.com/${ghOrg}/${prj}.git"

        sh "git config user.email fabric8-admin@googlegroups.com"
        sh "git config user.name fabric8-release"
        sh "git remote set-url origin git@github.com:${ghOrg}/${prj}.git"
        def version
        container(name: 'go') {
            stage ('build binary'){
                sh 'chmod 600 /root/.ssh-git/ssh-key'
                sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
                sh 'chmod 700 /root/.ssh-git'

                if (!fileExists('version/VERSION')){
                    error 'no version/VERSION found'
                }

                sh "gobump -f version/VERSION patch"
                sh "git commit -am 'Version bump'"
                version = readFile('version/VERSION').trim()

                sh "git push origin master"

                def token = new io.fabric8.Fabric8Commands().getGitHubToken()
                sh "export GITHUB_ACCESS_TOKEN=${token}; make -e BRANCH=master release"
            }
        }

        container(name: 'docker') {
            def imageName = "docker.io/${dockerOrg}/${prj}"

            stage ('build image'){
                sh "docker build -t ${imageName}:latest ."
            }

            stage ('push latest images'){
                sh "docker push ${imageName}:latest"
                sh "docker tag ${imageName}:latest ${imageName}:${version}"
                sh "docker push ${imageName}:${version}"
            }
        }
    }

  }
