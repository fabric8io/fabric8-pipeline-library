import groovy.json.JsonSlurper;

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    node {
      ws (config.name){
        def flow = new io.fabric8.Release()
        flow.setupWorkspace (config.name)

        gitRepo = flow.getGitRepo()

        URL apiUrl = new URL("https://api.github.com/repos/${gitRepo}/${config.name}/pulls/${config.prId}")
        def branchName
        def notified = false

        // wait until the PR is merged, if there's a merge conflict the notify and wait until PR is finally merged
        waitUntil {
          def pr = new JsonSlurper().parse(apiUrl.newReader())
          branchName = pr.head.ref

          if (pr.mergeable_state == 'dirty' && !notified){

            def message = """Pull request was not automatically merged.  Please fix and update Pull Request to continue with release...

            git fetch origin pull/${config.prId}/head:fixPR${config.prId}

            git checkout fixPR${config.prId}

            [resolve issue]

            git commit -a -m 'resolved merge issues caused by release dependency updates'
            git push origin fixPR${config.prId}:${branchName}
            """
            hubotProject message
            notified = true
          }
          pr.merged == true
        }
        // clean up
        sh "git push origin --delete ${branchName}"
      }
    }
}
