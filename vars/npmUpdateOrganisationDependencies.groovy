#!/usr/bin/groovy
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurperClassic
import io.fabric8.Fabric8Commands
import io.fabric8.Utils
import java.util.LinkedHashMap

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def organisation = config.organisation
    def repoNames = config.repos
    def packageLocation = 'package.json'
    def containerName = config.containerName ?: 'clients'
    def localPackageJSON = readFile file: packageLocation
    def replaceVersions = loadPackagePropertyVersions(localPackageJSON)
    def newPRID

    def flow = new Fabric8Commands()
    def utils = new Utils()

    println "About to try replace versions: '${replaceVersions}'"

    if (replaceVersions.size() > 0) {
        // create individual PRs for every version upgrade PR we need to make
        for (pair in replaceVersions) {

            def property = pair[0]
            def version = pair[1]

            println "Now updating all projects within organisation: ${organisation}"

            // get the repos we want to update
            def repos
            if (repoNames?.trim()) {
                repos = splitRepoNames(repoNames)
            } else {
                repos = getRepos(organisation)
            }

            for (repo in repos) {
                repo = repo.toString().trim()
                def project = "${organisation}/${repo}"

                // lets check if the repo has a package.son
                packageUrl = new URL("https://raw.githubusercontent.com/${organisation}/${repo}/master/package.json")
                def hasPackage = false
                try {
                    hasPackage = !packageUrl.text.isEmpty()
                } catch (FileNotFoundException e1) {
                    // ignore
                }

                if (hasPackage) {
                    // skip if there's a version update PR already open'
                    def existingPR
                    container(name: containerName) {
                        existingPR = utils.getExistingPR(project, pair)
                    }

                    if (!existingPR) {
                        stage ("Updating ${project}"){

                            sh "rm -rf ${repo}"
                            sh "git clone https://github.com/${project}.git"
                            dir(repo) {
                                sh "git remote set-url origin git@github.com:${project}.git"

                                def uid = UUID.randomUUID().toString()
                                sh "git checkout -b versionUpdate${uid}"
                                utils.replacePackageVersion("${packageLocation}", pair)

                                def gitStatus = sh(script: "git status", returnStdout: true).toString().trim()

                                if (gitStatus != null && !gitStatus.contains('nothing to commit')) {

                                    container(name: containerName) {

                                        sh 'chmod 600 /root/.ssh-git/ssh-key'
                                        sh 'chmod 600 /root/.ssh-git/ssh-key.pub'
                                        sh 'chmod 700 /root/.ssh-git'

                                        sh "git config --global user.email fabric8-admin@googlegroups.com"
                                        sh "git config --global user.name fabric8-release"

                                        def message = "fix(version): update ${property} to ${version}"
                                        sh "git add ${packageLocation}"

                                        sh "git commit -m \"${message}\""

                                        try {
                                            sh "git push origin versionUpdate${uid}"
                                            newPRID = flow.createPullRequest("${message}", "${project}", "versionUpdate${uid}")

                                        } catch (err) {
                                            def msg = """
                                            Skipping NPM version update for ${project}

                                            ERROR: ${err}
                                            """
                                            hubotSend message: msg, failOnError: false
                                            echo "${msg}"
                                        }
                                    }
                                } else {
                                    echo "No changes found skipping"
                                }
                            }
                        }
                    } else {
                        echo "found existing PR ${existingPR} so skipping"
                    }

                } else {
                    println "Ignoring project ${project} as it has no package.json"
                }
            }
        }
    }
}

@NonCPS
def loadPackagePropertyVersions(String json) {
    println "Finding property versions from JSON "
    // HashMap isn't serializable and NonCPS causes issues in SED command later so use a list
    def replaceVersions = []
    LinkedHashMap j = new JsonSlurperClassic().parseText(json)
    for (def d : j.dependencies) {
        replaceVersions << [d.key, d.value]
    }

    return replaceVersions
}

@NonCPS
def getRepos(String organisation) {
    repoApi = new URL("https://api.github.com/orgs/${organisation}/repos?per_page=500")
    repos = new JsonSlurperClassic().parse(repoApi.newReader())

    def list = []
    for (repoData in repos) {
        println "project to process ${organisation}/${repoData.name}"
        list << repoData.name
    }
    repos = null
    return list
}

@NonCPS
def splitRepoNames(repoNames) {
    def repos = repoNames.split(',')
    def list = []
    for (name in repos) {
        echo "project to process ${name}"
        list << name
    }
    repos = null
    return list
}
