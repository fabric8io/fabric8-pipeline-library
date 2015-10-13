import groovy.json.JsonSlurper;

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    node ('swarm'){
      ws (config.name){
        authString = "${env.GITHUB_TOKEN}"
        def flow = new io.fabric8.Release()
        flow.setupWorkspace (config.name)
        echo "working for pull request id ${config.prId}"
        gitRepo = flow.getGitRepo()
        String id = config.prId
        echo "working for pull request id ${id}"
        def apiUrl = new URL("https://api.github.com/repos/${gitRepo}/${config.name}/pulls/${id}")

        def branchName
        def notified = false

        // wait until the PR is merged, if there's a merge conflict the notify and wait until PR is finally merged
        waitUntil {
          def HttpURLConnection connection = apiUrl.openConnection()
          if(authString.length() > 0)
          {
            def conn = apiUrl.openConnection()
            connection.setRequestProperty("Authorization", "Bearer ${authString}")
          }
          connection.setRequestMethod("GET")
          connection.setDoInput(true)
          connection.connect()
          def pr = new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(),"UTF-8"))
          connection.disconnect()


          branchName = pr.head.ref
          echo "${branchName}"
          echo "${pr.mergeable_state}"

          if (pr.mergeable_state == 'unstable' && !notified){
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
          pr.merged == true
        }
        try {
          // clean up
          sh "git push origin --delete ${branchName}"
        } catch (err) {
          echo "not able to delete repo: ${err}"
        }
      }
    }
}
