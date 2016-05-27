<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**

- [Fabric8 Jenkins Workflow Library](#fabric8-jenkins-workflow-library)
  - [Requirements](#requirements)
  - [How it works](#how-it-works)
  - [Functions from the Jenkins global library](#functions-from-the-jenkins-global-library)
    - [Approve](#approve)
    - [Deploy Project](#deploy-project)
    - [Drop Project](#drop-project)
    - [Get Kubernetes JSON](#get)
    - [Get New Version](#get-new-version)
    - [Maven Canary Release](#maven-canary-release)
    - [Maven Integration Test](#maven-integration-test)
    - [Merge and Wait for Pull Request](#merge-and-wait-for-pull-request)
    - [Perform Canary Release](#perform-canary-release)
    - [REST Get URL](#rest-get-url)
    - [Update Maven Property Version](#update-maven-property-version)
    - [Wait Until Artifact Synced With Maven Central](#wait-until-artifact-synced-with-maven-central)
    - [Wait Until Pull Request Merged](#wait-until-pull-request-merged)
  - [fabric8 release functions](#fabric8-release)
    - [Promote Artifacts](#promote-artifacts)
    - [Release Project](#release-project)
    - [Stage Extra Images](#stage-extra-images)
    - [Stage Project](#stage-project)
    - [Tag Images](#tag-images)
    - [Git Tag](#git-tag)
    - [Deploy Remote OpenShift](#deploy-remote-openshift)
    - [Deploy Remote Kubernetes](#deploy-remote-kubernetes)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Fabric8 Jenkins Workflow Library

This git repository contains a library of reusable [Jenkins Pipeline](https://github.com/jenkinsci/workflow-plugin) scripts that can be used on a project.

<p align="center">
  <a href="http://fabric8.io/guide/cdelivery.html">
  	<img src="https://raw.githubusercontent.com/fabric8io/fabric8/master/docs/images/cover/cover_small.png" alt="fabric8 logo"/>
  </a>
</p>

The idea is to try promote sharing of workflows across projects where it makes sense.

You can then either

* reuse any of the flows as is
* fork this repository and make your own changes (hopefully submitting a Pull Request back)
* copy flows from this project into your own projects source code where you can modify it further

### Requirements

These flows make use of the [Fabric8 DevOps Workflow Steps](https://github.com/fabric8io/fabric8-jenkins-workflow-steps) and [kubernetes-worflow](https://github.com/fabric8io/kubernetes-workflow) which help when working with [Fabric8 DevOps](http://fabric8.io/guide/cdelivery.html) in particular for clean integration with the [Hubot chat bot](https://hubot.github.com/) and human approval of staging, promotion and releasing.

### How it works

When the fabric8 Jenkins app is run, we use a kubernetes post start script to populate the Jenkins internal global library with scripts from this repo.  We are then able to easily call the groovy functions from within our Jenkinsfiles.

These scripts are baked into the fabric8 jenkins docker image during the fabric8 release however you can override this repo by updating the parameters when running the Jenkins app from the fabric8 console.

### Functions from the Jenkins global library

#### Approve

- requests approval in a pipeline
- hubot integration prompting chat room for approvals including links to environments
- sends events to elasticsearch if running in a configured namespace, this helps OOTB charting of approval wait times

example..
```groovy
    approve{
      version = '0.0.1'
      console = 'http://fabric8.kubernetes.fabric8.io'
      environment = 'staging'
    }
```
#### Deploy Project

- applies a kubernetes json resource to the host OpenShift / Kubernetes cluster
- lazily creates the environment if it doesn't already exist
```groovy
    deployProject{
      stagedProject = 'my-project'
      resourceLocation = 'target/classes/kubernetes.json'
      environment = 'staging'
    }
```
#### Drop Project

in the case of an aborted approval

- will drop the an OSS sonartype staged repository
- close any pull requests that have been created based on the release
- delete the branch relating to the PR mentioned above
```groovy
    dropProject{
      stagedProject = project
      pullRequestId = '1234'
    }
```
#### Get Kubernetes JSON

- returns a default OpenShift templates that gets translated into Kubernetes List when applied by kubernetes-workflow apply step and running on Kubernetes
- returns a service and replication controller JSON using sensible defaults
- can be used in conjunction with [kubernetesApply](https://github.com/fabric8io/kubernetes-workflow/tree/master/devops-steps#applying-kubernetes-configuration)
```groovy
    node {
        def rc = getKubernetesJson {
          port = 8080
          label = 'node'
          icon = 'https://cdn.rawgit.com/fabric8io/fabric8/dc05040/website/src/images/logos/nodejs.svg'
          version = '0.0.1'
        }

        kubernetesApply(file: rc, environment: 'my-cool-app-staging', registry: 'myexternalregistry.io:5000')
    }
```
#### Get New Version

- returns the short git sha for the current project to be used as a version
```groovy
    def newVersion = getNewVersion{}
```
#### Maven Canary Release

- creates a release branch
- sets the maven pom versions using versions-maven-plugin
- runs `mvn deploy docker:build`
- generates maven site and deploys it to the content repository
```groovy
    mavenCanaryRelease{
      version = canaryVersion
    }
```
#### Maven Integration Test

- lazily creates a test environment in kubernetes
- runs maven integration tests in test environment
```groovy
    mavenIntegrationTest{
      environment = 'Testing'
      failIfNoTests = 'false'
      itestPattern = '*KT'
    }
```
#### Merge and Wait for Pull Request

- adds a [merge] comment to a github pull request
- waits for GitHub pull request to be merged by an external CI system
```groovy
    mergeAndWaitForPullRequest{
      project = 'fabric8/fabric8'
      pullRequestId = prId
    }
```
#### Perform Canary Release

- generic function used by non Java based project
- gets a new version based on the short git sha
- builds docker image using a Dockerfile in the root of the project
- tags the image with the release version and prefixes the private fabric8 docker registry for the current namespace
- if running in a multi node cluster will perform a docker push.  Not needed in a single node setup as image built and cached locally
```groovy
    stage 'Canary release'
    echo 'NOTE: running pipelines for the first time will take longer as build and base docker images are pulled onto the node'
    if (!fileExists ('Dockerfile')) {
      writeFile file: 'Dockerfile', text: 'FROM django:onbuild'
    }

    def newVersion = performCanaryRelease {}
```
#### REST Get URL
- utility function returning the JSON contents of a REST Get request
```groovy
    def apiUrl = new URL("https://api.github.com/repos/${config.name}/pulls/${id}")
    JsonSlurper rs = restGetURL{
      authString = githubToken
      url = apiUrl
    }
```
#### Update Maven Property Version
During a release involving multiple java projects we often need to update downstream maven poms with new versions of a dependency.  In a release pipeline we want to automate this, set up a pull request and let CI run to make sure there's no conflicts.  

- performs a search and replace in the maven pom
- finds the latest version available in maven central (repo is configurable)
- if newer version exists pom is updated
- pull request submitted
- pipeline will wait until this is merged before continuing

If CI fails and updates are required as a result of the dependency upgrade then
- pipeline will notify a chat room (we use Slack)
- informs the team of the git commands needed to clone, switch to the version update branch and command to push back once fixed
- pipeline will wait until the CI passes before continuing

Automating this has saved us a lot of time during the release pipeline
```groovy
    def properties = []
    properties << ['<fabric8.version>','io/fabric8/kubernetes-api']
    properties << ['<docker.maven.plugin.version>','io/fabric8/docker-maven-plugin']

    updatePropertyVersion{
      updates = properties
      repository = source // if null defaults to http://central.maven.org/maven2/
      project = 'fabric8io/ipaas-quickstarts'
    }
```
#### Wait Until Artifact Synced With Maven Central
When working with open source java projects we need to stage artifacts with OSS Sonartype in order to promote them into maven central.  This can take 10-30 mins depending on the size of the artifacts being synced.  

A useful thing is to be notified in chat when artifacts are available in maven central as blocking the pipeine until we're sure the promote has worked.

- polls waiting for artifacts to be available in maven central
```groovy
    waitUntilArtifactSyncedWithCentral {
      repo = 'http://central.maven.org/maven2/'
      groupId = 'io.fabric8.archetypes'
      artifactId = 'archetypes-catalog'
      version = '0.0.1'
      ext = 'jar'
    }
```
#### Wait Until Pull Request Merged
During a CD pipeline we often need to wait for external events to complete before continuing.  One of the most common events we have on the fabric8 project is waiting for CI jobs or manually review and approval of github pull requests.  We don't want to fail a pipeline, rather just wait patiently for the pull requests to merge so we can continue.

- pull request submitted
- pipeline will wait until this is merged before continuing

If CI fails and updates are required as a result of the dependency upgrade then
- pipeline will notify a chat room (we use Slack)
- informs the team of the git commands needed to clone, switch to the version update branch and command to push back once fixed
- pipeline will wait until the CI passes before continuing

```groovy
    waitUntilPullRequestMerged{
      name = 'fabric8io/fabric8'
      prId = '1234'
    }
```
### fabric8 release

These functions are focused specifically on the fabric8 release itself however could be used as examples or extended in users own setup.

The core fabric8 release consists of multiple Java projects that generate Java artifacts, docker images and kubernetes resources.  These projects are built and staged together, automatically deployed into a test environment and after approval promoted together ready for the community to use.

When a project is staged an array is returned and passed around functions further down the pipeline.  The structure of this stagedProject array is in the form `[config.project, releaseVersion, repoId]`

- __config.project__ the name of the github project being released e.g. 'fabric8io/fabric8'
- __releaseVersion__ the new version e.g. '0.0.1'
- __repoId__ the OSS Sonartype staging repository Id used to interact with Sonartype later on

```groovy
    def stagedProject = stageProject{
      project = 'fabric8io/ipaas-quickstarts'
      useGitTagForNextVersion = true
    }
```

One other important note is on the fabric8 project we don't use the maven release plugin or update to next SNAPSHOT versions as it causes unwanted noise and commits to our many github repos.  Instead we use a fixed development `x.x-SNAPSHOT` version so we can easily work in development on multiple projects that have maven dependencies with each other.  

Now that we don't store the next release version in the poms we need to figure it out during the release.  Rather than store the version number in the repo which involves a commit and not too CD friendly (i.e. would trigger another release just for the version update) we use the `git tag`.  From this we can get the previous release version, increment it and push it back without triggering another release.  This seems a bit strange but it has been holding up and has significantly reduced unwanted SCM commits related to maven releases.

#### Promote Artifacts
- releases OSS sonartype staging repository so that artifacts are synced with maven central
- commits generated Helm charts to the fabric8 Help repo
- if useGitTagForNextVersion is set (true by default) then the next snapshot development version PR is committed
```groovy
    String pullRequestId = promoteArtifacts {
      projectStagingDetails = config.stagedProject
      project = 'fabric8io/fabric8'
      useGitTagForNextVersion = true
      helmPush = false
    }
```
#### Release Project
- promotes artifacts from OSS sonartype staging repo to maven central
- promotes images from internal docker registry to dockerhub
- waits for github pull request to merge if updating next snapshot version (not used by default)
- waits for artifacts to be synced and available in maven central
- sends chat notification when artifacts appear in maven central
```groovy
    releaseProject{
      stagedProject = project
      useGitTagForNextVersion = true
      helmPush = false
      groupId = 'io.fabric8.archetypes'
      githubOrganisation = 'fabric8io'
      artifactIdToWatchInCentral = 'archetypes-catalog'
      artifactExtensionToWatchInCentral = 'jar'
    }
```
#### Stage Extra Images

- takes a list of external images not built by the CD pipeline which need tagging in dockerhub with the new release version
- pulls the latest images from dockerhub
- tags them with the new fabric8 release
- stages them in the internal docker registry
```groovy
    stageExtraImages {
      images = ['gogs','jenkins','taiga']
      tag = releaseVersion
    }
```
#### Stage Project
- builds and stages a fabric8 java project with OSS sonartype
- build docker images and stages them in the internal docker registry
- stages extra images not built by docker-maven-plugin in the internal docker registry
```groovy
    def stagedProject = stageProject{
      project = 'fabric8io/ipaas-quickstarts'
      useGitTagForNextVersion = true
    }
```
#### Tag Images
- will pull external images which have been staged in the fabric8 docker registry and push the new tag to dockerhub
```groovy
    tagImages{
      images = ['gogs','jenkins','taiga']
      tag = releaseVersion
    }
```
#### Git Tag

- tags the current git repo with the provided version  
- pushes the tag to the remote repository  

```groovy
    gitTag{
      releaseVersion = '0.0.1'
    }
```
#### Deploy Remote OpenShift

Deploys the staged fabric8 release to a remote OpenShift cluster

__NOTE__ in order for images to be found by the the remote OpenShift instance it must be able to pull images from the staging docker registry.  Noting private networks and insecure-registry flags.

```groovy
    node{
      deployRemoteOpenShift{
        url = openshiftUrl
        domain = 'staging'
        stagingDockerRegistry = openshiftStagingDockerRegistryUrl
      }
    }
```

#### Deploy Remote Kubernetes

Deploys the staged fabric8 release to a remote Kubernetes cluster  

__NOTE__ in order for images to be found by the the remote OpenShift instance it must be able to pull images from the staging docker registry.  Noting private networks and insecure-registry flags.    

```groovy
    node{
      deployRemoteKubernetes{
        url = kubernetesUrl
        defaultNamespace = 'default'
        stagingDockerRegistry = kubernetesStagingDockerRegistryUrl
      }
    }
```
