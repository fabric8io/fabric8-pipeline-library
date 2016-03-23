#!/usr/bin/groovy
def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def flow = new io.fabric8.Fabric8Commands()
  flow.addMergeCommentToPullRequest(config.pullRequestId, config.project)
  waitUntilPullRequestMerged{
    name = config.project
    prId = config.pullRequestId
  }

}
