#!/usr/bin/groovy
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper
import groovy.xml.DOMBuilder
import groovy.xml.XmlUtil
import groovy.xml.dom.DOMCategory
import io.fabric8.Fabric8Commands
import org.w3c.dom.Element

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def organisation = config.organisation
    def repoNames = config.repos
    def pomLocation = 'pom.xml'
    def containerName = config.containerName ?: 'clients'

    def flow = new Fabric8Commands()

    def replaceVersions = [:]
    def localPomXml = readFile file: pomLocation
    loadPomPropertyVersions(localPomXml, replaceVersions)

    println "About to try replace versions: '${replaceVersions}'"

    if (replaceVersions.size() > 0) {
        println "Now updating all projects within organisation: ${organisation}"

        def repos;
        if (repoNames?.trim()){
            repos = repoNames.split(',')
        }else {
           repos = getRepos(organisation)
        }

        for (repo in repos) {
            repo = repo.toString().trim()
            def project = "${organisation}/${repo}"

            // lets check if the repo has a pom.xml
            pomUrl = new URL("https://raw.githubusercontent.com/${organisation}/${repo}/master/pom.xml")
            def hasPom = false
            try {
                hasPom = !pomUrl.text.isEmpty()
            } catch( FileNotFoundException e1 ) {
                // ignore

            }

            if (hasPom) {
                stage "Updating ${project}"
                sh "rm -rf ${repo}"
                sh "git clone https://github.com/${project}.git"
                sh "cd ${repo} && git remote set-url origin git@github.com:${project}.git"

                def uid = UUID.randomUUID().toString()
                sh "cd ${repo} && git checkout -b versionUpdate${uid}"

                def xml = readFile file: "${repo}/${pomLocation}"
                //sh "cat ${repo}/${pomLocation}"

                def changed = false
                for (entry in replaceVersions) {
                    def pom = updateVersion(project, xml, entry.key, entry.value)
                    if (pom != null) {
                        xml = pom
                        changed = true
                    }
                }
                if (changed) {
                    writeFile file: "${repo}/${pomLocation}", text: xml
                    println "updated file ${repo}/${pomLocation}"

                    //sh "cat ${repo}/${pomLocation}"

                    container(name: containerName) {

                        sh 'chmod 600 /root/.ssh-git/ssh-key'
                        sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
                        sh 'chmod 700 /root/.ssh-git'

                        sh "git config --global user.email fabric8-admin@googlegroups.com"
                        sh "git config --global user.name fabric8-release"

                        def message = "\"Update pom property versions\""
                        sh "cd ${repo} && git add ${pomLocation}"
                        sh "cd ${repo} && git commit -m ${message}"
                        sh "cd ${repo} && git push origin versionUpdate${uid}"
                        retry(5) {
                            String ghToken = readFile '/home/jenkins/.apitoken/hub'
                            wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [
                                [password: ghToken, var: 'GH_PASSWORD']]]) {

                                sh "export GITHUB_TOKEN=${ghToken} && cd ${repo} && hub pull-request -m ${message} > pr.txt"
                            }   
                        }
                    }
                    pr = readFile("${repo}/pr.txt")
                    split = pr.split('\\/')
                    def id = split[6].trim()
                    println "received Pull Request Id: ${id}"
                    flow.addMergeCommentToPullRequest(id, project)

                    waitUntilPullRequestMerged{
                        name = project
                        prId = id
                    }
                }
            } else {
                println "Ignoring project ${project} as it has no pom.xml"
            }
        }
    }

  }

@NonCPS
def loadPomPropertyVersions(String xml, replaceVersions) {
    println "Finding property versions from XML"

    try {
        def index = xml.indexOf('<project')
        def header = xml.take(index)
        def xmlDom = DOMBuilder.newInstance().parseText(xml)
        def propertiesList = xmlDom.getElementsByTagName("properties")
        if (propertiesList.length == 0) {
            println "No <properties> element found in pom.xml!"
        } else {
            def propertiesElement = propertiesList.item(0)
            for (node in propertiesElement.childNodes) {
                if (node instanceof Element) {
                    replaceVersions[node.nodeName] = node.textContent
                }
            }
        }
        println "Have loaded replaceVersions ${replaceVersions}"
    } catch (e) {
        println "Failed to parse XML due to: ${e}"
        e.printStackTrace()
        throw e
    }
}

@NonCPS
def getRepos(String organisation){
    repoApi = new URL("https://api.github.com/orgs/${organisation}/repos?per_page=100")
    repos = new JsonSlurper().parse(repoApi.newReader())

    def list = []
    for (repoData in repos) {
        println "project to process ${organisation}/${repoData.name}"
        list << repoData.name
    }
    repos = null
    return list
}

@NonCPS
def updateVersion(project, xml, elementName, newVersion) {
    def index = xml.indexOf('<project')
    def header = xml.take(index)
    def xmlDom = DOMBuilder.newInstance().parseText(xml)
    def root = xmlDom.documentElement
    use(DOMCategory) {
        def versions = xmlDom.getElementsByTagName(elementName)
        if (versions.length == 0) {
            //println "project ${project} pom.xml does not contain property: ${elementName}"
            return null
        } else {
            def version = versions.item(0)
            if (newVersion != version.textContent) {
                println "project ${project} updated property ${elementName} to ${newVersion}"
                version.textContent = newVersion

                def newXml = XmlUtil.serialize(root)
                // need to fix this, we get errors above then next time round if this is left in
                return header + newXml.minus('<?xml version="1.0" encoding="UTF-8"?>')
            } else {
                return null
            }
        }
    }
}
