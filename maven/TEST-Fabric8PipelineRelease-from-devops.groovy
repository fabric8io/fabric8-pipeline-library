  stage 'test'
  parallel(quickstarts: {
    String quickstartPr = '794'
    if (quickstartPr != null){
      waitUntilPullRequestMerged{
        name = 'ipaas-quickstarts'
        prId = quickstartPr
      }
    }
  }, devops: {
    String devopsPr = '91'
    if (devopsPr != null){
      waitUntilPullRequestMerged{
        name = 'fabric8-devops'
        prId = devopsPr
      }
    }
  }, ipaas: {
    String ipaasPr = '31'
    if (ipaasPr != null){
      waitUntilPullRequestMerged{
        name = 'fabric8-ipaas'
        prId = ipaasPr
      }
    }
  })
