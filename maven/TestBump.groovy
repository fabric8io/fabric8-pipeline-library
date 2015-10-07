
def stagedRepo = []

stage 'one'
// devopsPr = bumpFabric8DevOpsVersions{}

devopsPr = testerReturn{}

if (devopsPr != null){
  echo "got and id back ${devopsPr}"
  String devopsPr = (String) devopsPr
  waitUntilPullRequestMerged{
    name = 'fabric8-devops'
    prId = devopsPr
  }
}
