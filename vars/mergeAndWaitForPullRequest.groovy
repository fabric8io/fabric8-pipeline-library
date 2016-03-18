#!/usr/bin/groovy
def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  stage "Merge PR ${config.project}/${pullRequestId}"

    def flow = new io.fabric8.Fabric8Commands()
    flow.addMergeCommentToPullRequest(pullRequestId, config.project)
    waitUntilPullRequestMerged{
      name = config.project
      prId = pullRequestId
    }

}
