#!/usr/bin/groovy
import com.cloudbees.groovy.cps.NonCPS

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def flow = new io.fabric8.Fabric8Commands()

  def dockerfileLocation = config.parentDockerfileLocation ?: 'Dockerfile'
  def containerName = config.containerName ?: 'clients'
  def autoMerge = config.autoMerge ?: false

  for (int i = 0; i < config.projects.size(); i++) {
    def project = config.projects[i]
    def items = project.split('/')
    def org = items[0]
    def dockerImage = config.propertyName
    def tag = config.version
    def id

    ws{
      stage "Updating ${project}"

      git "https://github.com/${project}.git"
      sh "git remote set-url origin git@github.com:${project}.git"

      def uid = UUID.randomUUID().toString()
      sh "git checkout -b updateDockerfileFromTag${uid}"

      def dockerfile = readFile file: "${dockerfileLocation}"
      sh "cat ${dockerfileLocation}"

      sh "sed -i 's/FROM.*${dockerImage}.*/FROM ${dockerImage}:${tag}/g' ${dockerfileLocation}"

      sh "cat ${dockerfileLocation}"

      def newDockerfile = readFile file: "${dockerfileLocation}"

      if (newDockerfile != null) {
        writeFile file: "${dockerfileLocation}", text: newDockerfile

        sh "cat ${dockerfileLocation}"

        container(name: containerName) {

          sh 'chmod 600 /root/.ssh-git/ssh-key'
          sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
          sh 'chmod 700 /root/.ssh-git'

          sh "git config --global user.email fabric8-admin@googlegroups.com"
          sh "git config --global user.name fabric8-release"

          def message = "Update Dockerfile base image tag ${config.propertyName} to ${config.version}"
          sh "git add ${dockerfileLocation}"
          sh "git commit -m \"${message}\""
          sh "git push origin updateDockerfileFromTag${uid}"

          id = flow.createPullRequest("${message}","${project}","updateDockerfileFromTag${uid}")
        }
        echo "received Pull Request Id: ${id}"

        if (autoMerge){
          sleep 5 // give a bit of time for GitHub to get itself in order after the new PR
          flow.mergePR(project, id, "updateDockerfileFromTag${uid}")
        } else {
          flow.addMergeCommentToPullRequest(id, project)
          waitUntilPullRequestMerged{
            name = project
            prId = id
          }
        }
      }
    }
  }
}
