
def stagedRepo = []

stage 'one'
waitUntilPullRequestMerged{
  name = 'fabric8-devops'
  prId = '79'
}
