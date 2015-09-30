
def stagedRepo = []

stage 'one'
waitUntilPullRequestMerged{
  name = 'kubernetes-client'
  prId = '3'
}
