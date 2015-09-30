def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    node {
      def flow = new io.fabric8.Release()
      def notified = false
      waitUntil {
        def pr = new JsonSlurper().parse("https://api.github.com/repos/${gitRepo}/${project}/pulls/${id}")

        if (pr.mergeable_state == 'dirty' && !notified){
          def branchName = flow.getBranchName(config.name, config.prId)
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
        pr.merged == 'true'
      }
      sh "git push origin --delete ${branchName}"
    }
}
