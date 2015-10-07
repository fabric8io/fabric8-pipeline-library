def release = ""
try {
  release = IS_RELEASE
} catch (Throwable e) {
  release = "${env.IS_RELEASE ?: 'false'}"
}

def stagedProjects = []


  stage 'bump apps and quickstarts release dependency versions'


  stage 'stage apps and quickstarts release'
  parallel(quickstarts: {
    stagedProjects << testProject{
      project = 'ipaas-quickstarts'
    }
  }, devops: {
    stagedProjects << testProject{
      project = 'fabric8-devops'
    }
  }, ipaas: {
    stagedProjects << testProject{
      project = 'fabric8-ipaas'
    }
  })


for(int j = 0; j < stagedProjects.size(); j++){
  echo "YAY ${stagedProjects[j][0]}"
  echo "YAY ${stagedProjects[j][1]}"
  echo "YAY ${stagedProjects[j][2]}"
}
