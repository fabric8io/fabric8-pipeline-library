#!/usr/bin/groovy
import groovy.xml.DOMBuilder
import groovy.xml.XmlUtil
import groovy.xml.dom.DOMCategory

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def flow = new io.fabric8.Fabric8Commands()

  def pomLocation = config.parentPomLocation ?: 'pom.xml'

  for (int i = 0; i < config.projects.size(); i++) {
    def project = config.projects[i]
    def items = project.split('/')
    def org = items[0]
    def repo = items[1]
    def id

    stage "Updating ${project}"
    sh "rm -rf ${repo}"
    sh "git clone https://github.com/${project}.git"
    sh "cd ${repo} && git remote set-url origin git@github.com:${project}.git"

    def uid = UUID.randomUUID().toString()
    sh "cd ${repo} && git checkout -b versionUpdate${uid}"

    def xml = readFile file: "${repo}/${pomLocation}"
    sh "cat ${repo}/${pomLocation}"

    def pom = updateVersion(xml, config.propertyName, config.version)

    if (pom != null) {
      writeFile file: "${repo}/${pomLocation}", text: pom

      sh "cat ${repo}/${pomLocation}"

      kubernetes.pod('buildpod').withImage('fabric8/maven-builder:latest')
              .withPrivileged(true)
              .withSecret('jenkins-git-ssh', '/root/.ssh-git')
              .withSecret('jenkins-ssh-config', '/root/.ssh')
              .inside {

        sh 'chmod 600 /root/.ssh-git/ssh-key'
        sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
        sh 'chmod 700 /root/.ssh-git'

        sh "git config --global user.email fabric8-admin@googlegroups.com"
        sh "git config --global user.name fabric8-release"

        def githubToken = flow.getGitHubToken()
        def message = "Update pom property ${config.propertyName} to ${config.version}"
        sh "cd ${repo} && git add ${pomLocation}"
        sh "cd ${repo} && git commit -m \"${message}\""
        sh "cd ${repo} && git push origin versionUpdate${uid}"

        id = flow.createPullRequest("${message}","${project}","versionUpdate${uid}")
      }
      echo "received Pull Request Id: ${id}"
      flow.addMergeCommentToPullRequest(id, project)

      waitUntilPullRequestMerged{
        name = project
        prId = id
      }
    }
  }
}

@NonCPS
def updateVersion(xml, elementName, newVersion) {
  def index = xml.indexOf('<project')
  def header = xml.take(index)
  def xmlDom = DOMBuilder.newInstance().parseText(xml)
  def root = xmlDom.documentElement
  use(DOMCategory) {
    def versions = xmlDom.getElementsByTagName(elementName)
    if (versions.length == 0) {
      echo "No element found called ${elementName}"
    } else {
      def version = versions.item(0)
      echo "version ${elementName} = ${version.textContent}"
      version.textContent = newVersion

      def newXml = XmlUtil.serialize(root)
      // need to fix this, we get errors above then next time round if this is left in
      return header + newXml.minus('<?xml version="1.0" encoding="UTF-8"?>')
    }
  }
}
