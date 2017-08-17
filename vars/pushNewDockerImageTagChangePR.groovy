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
    def repo = items[1]
    def dockerImage = config.propertyName
    def tag = config.version
    def id

    ws{
      stage "Updating ${project}"
      sh "rm -rf ${repo}"
  
      git "https://github.com/${project}.git"
      sh "git remote set-url origin git@github.com:${project}.git"

      def uid = UUID.randomUUID().toString()
      sh "cd ${repo} && git checkout -b updateDockerfileFromTag${uid}"

      def dockerfile = readFile file: "${repo}/${dockerfileLocation}"
      sh "cat ${repo}/${dockerfileLocation}"

      sh "sed -i 's/FROM.*${dockerImage}.*/FROM ${dockerImage}:${tag}/g' ${repo}/${dockerfileLocation}"

      sh "cat ${repo}/${dockerfileLocation}"

      def newDockerfile = readFile file: "${repo}/${dockerfileLocation}"

      if (newDockerfile != null) {
        writeFile file: "${repo}/${dockerfileLocation}", text: newDockerfile

        sh "cat ${repo}/${dockerfileLocation}"

        container(name: containerName) {

          sh 'chmod 600 /root/.ssh-git/ssh-key'
          sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
          sh 'chmod 700 /root/.ssh-git'

          sh "git config --global user.email fabric8-admin@googlegroups.com"
          sh "git config --global user.name fabric8-release"

          def message = "Update Dockerfile base image tag ${config.propertyName} to ${config.version}"
          sh "cd ${repo} && git add ${dockerfileLocation}"
          sh "cd ${repo} && git commit -m \"${message}\""
          sh "cd ${repo} && git push origin updateDockerfileFromTag${uid}"

          id = flow.createPullRequest("${message}","${project}","updateDockerfileFromTag${uid}")
        }
        echo "received Pull Request Id: ${id}"

        if (autoMerge){
          sleep 5 // give a bit of time for GitHub to get itself in order after the new PR
          flow.mergePR(project, id)
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
