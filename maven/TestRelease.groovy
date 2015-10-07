
def t = []

stage 'one'
t << ['ipaas-quickstarts','2.2.44',"['iofabric8-1717']"]
t << ['fabric8-ipaas','2.2.44',"['iofabric8-1718']"]
t << ['fabric8-devops','2.2.44',"['iofabric8-1719']"]
  // trigger pull requests
  stage 'release'
 parallel(a: {
    def quickstartsReleasePR = testrelease {
      g = t
      h = 'ipaas-quickstarts'
    }
 }, b: {
    def devopsReleasePR = testrelease {
      g = t
      h = 'fabric8-devops'
    }
  }, c: {
    ipaasReleasePR = testrelease {
      g = t
      h = 'fabric8-ipaas'
    }
  })
