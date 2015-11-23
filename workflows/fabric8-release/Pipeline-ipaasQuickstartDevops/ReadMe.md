# Pipeline from ipaas-quickstarts

Runs a CD pipeline starting with [ipaas-quickstarts](https://github.com/fabric8io/ipaas-quickstarts)

__NOTES__: ipaas-quickstarts generates maven archetypes.  The version that is released is then referenced downstream of the fabric8-devops repo in (`<fabric8.archetypes.release.version>2.2.xx</fabric8.archetypes.release.version>`) and used by fabric8-forge.  Therefore the order these projects are released is important.

The workflow will first check to see if there's a newer version of any fabric8 dependencies from a previous stage available, if there are then the workflow will update the dependency and submit a pull request so that the CI tests run.  Upon success of the CI job the dependency update pull request will be merged.

Artifacts released in this order:

1. [ipaas-quickstarts](https://github.com/fabric8io/ipaas-quickstarts)
2. In parallel: [fabric8-devops](https://github.com/fabric8io/fabric8-devops) & [fabric8-ipaas](https://github.com/fabric8io/fabric8-ipaas)

##Example pipeline view

TBC
