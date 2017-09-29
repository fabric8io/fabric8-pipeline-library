#!/usr/bin/groovy
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper;

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    def githubToken = flow.getGitHubToken()

    echo "pull request id ${config.prId}"
    String id = config.prId

    def branchName
    def notified = false

    // wait until the PR is merged, if there's a merge conflict the notify and wait until PR is finally merged
    waitUntil {
      echo "https://api.github.com/repos/${config.name}/pulls/${id}"

      def apiUrl = new URL("https://api.github.com/repos/${config.name}/pulls/${id}")

      def rs = restGetURL{
        authString = githubToken
        url = apiUrl
      }

      if (rs.merged == true){
          echo "PR ${id} merged"
          return true
      }

      if (rs.state == 'closed'){
          echo "PR ${id} closed"
          return true
      }

      branchName = rs.head.ref
      def sha = rs.head.sha
      echo "checking status of commit ${sha}"

      apiUrl = new URL("https://api.github.com/repos/${config.name}/commits/${sha}/status")
      rs = restGetURL{
        authString = githubToken
        url = apiUrl
      }

      echo "${config.name} Pull request ${id} state ${rs.state}"

        def values = config.name.split('/')
        def prj = values[1]

        if (rs.state == 'failure' && !notified){
        def message ="""
Pull request was not automatically merged.  Please fix and update Pull Request to continue with release...
```
git clone git@github.com:${config.name}.git
cd ${prj}
git fetch origin pull/${id}/head:fixPR${id}
git checkout fixPR${id}

  [resolve issue]

git commit -a -m 'resolved merge issues caused by release dependency updates'
git push origin fixPR${id}:${branchName}
```
"""

       hubotSend message: message, failOnError: false
            def shouldWeWait = requestResolve()

            if (!shouldWeWait){
                return true
            }
      notified = true
    }
    rs.state == 'success'
  }
  try {
      // clean up
      deleteGitHubBranch{
          authString = githubToken
          branch = branchName
          project = prj
      }

  } catch (err) {
    echo "not able to delete repo: ${err}"
  }
}


def requestResolve(){
    def proceedMessage = '''
Would you like do resolve the conflict?  If so please reply with the proceed command.

Alternatively you can skip this conflict.  This is highly discouraged but maybe necessary if we have a problem quickstart for example.
To do this chose the abort option below, note this particular action will not abort the release and only skip this conflict.
'''

    try{
        hubotApprove message: proceedMessage, failOnError: false
        return true
    } catch (err) {
        echo 'Skipping conflict'
        return false
    }
}
