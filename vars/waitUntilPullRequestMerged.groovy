#!/usr/bin/groovy
import groovy.json.JsonSlurper;

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    stage "waiting for ${config.name} ${config.prId} PR to merge"

    def flow = new io.fabric8.Fabric8Commands()
    def githubToken = flow.getGitHubToken()

        //flow.setupWorkspace (config.name)
    echo "pull request id ${config.prId}"
    gitRepo = flow.getGitRepo()
    String id = config.prId

    def branchName
    def notified = false

    // wait until the PR is merged, if there's a merge conflict the notify and wait until PR is finally merged
    waitUntil {
      def apiUrl = new URL("https://api.github.com/repos/${gitRepo}/${config.name}/pulls/${id}")
      JsonSlurper rs = restGetURL{
        authString = githubToken
        url = apiUrl
      }

      branchName = rs.head.ref
      def sha = rs.head.sha
      echo "checking status of commit ${sha}"

      apiUrl = new URL("https://api.github.com/repos/${gitRepo}/${config.name}/commits/${sha}/status")
      rs = restGetURL{
        authString = githubToken
        url = apiUrl
      }

      echo "${config.name} Pull request ${id} state ${rs.state}"

      if (rs.state == 'failure' && !notified){
        def message ="""
Pull request was not automatically merged.  Please fix and update Pull Request to continue with release...
```
  git clone git@github.com:${gitRepo}/${config.name}.git
  cd ${config.name}
  git fetch origin pull/${id}/head:fixPR${id}
  git checkout fixPR${id}

  [resolve issue]

  git commit -a -m 'resolved merge issues caused by release dependency updates'
  git push origin fixPR${id}:${branchName}
```
"""

      hubot room: 'release', message: message
      notified = true
    }
    rs.state == 'success'
  }
  try {
    // clean up
    sh "git push origin --delete ${branchName}"
  } catch (err) {
    echo "not able to delete repo: ${err}"
  }
}
