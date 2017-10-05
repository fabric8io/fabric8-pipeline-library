<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**

- [Fabric8 Pipeline Library](#fabric8-pipeline-library)
  - [How to use this library](#how-to-use-this-library)
    - [Making changes](#making-changes)
    - [Requirements](#requirements)
    - [Functions from the Jenkins global library](#functions-from-the-jenkins-global-library)
      - [Approve](#approve)
      - [Deploy Project](#deploy-project)
      - [Drop Project](#drop-project)
      - [Get Deployment Resources](#get-deployment-resources)
      - [Get Kubernetes JSON](#get-kubernetes-json)
      - [Get New Version](#get-new-version)
      - [Maven Canary Release](#maven-canary-release)
      - [Maven Integration Test](#maven-integration-test)
      - [Merge and Wait for Pull Request](#merge-and-wait-for-pull-request)
      - [Perform Canary Release](#perform-canary-release)
      - [REST Get URL](#rest-get-url)
      - [Update Maven Property Version](#update-maven-property-version)
      - [Wait Until Artifact Synced With Maven Central](#wait-until-artifact-synced-with-maven-central)
      - [Wait Until Pull Request Merged](#wait-until-pull-request-merged)
    - [fabric8 release](#fabric8-release)
      - [Promote Artifacts](#promote-artifacts)
      - [Release Project](#release-project)
      - [Stage Extra Images](#stage-extra-images)
      - [Stage Project](#stage-project)
      - [Tag Images](#tag-images)
      - [Git Tag](#git-tag)
      - [Deploy Remote OpenShift](#deploy-remote-openshift)
      - [Deploy Remote Kubernetes](#deploy-remote-kubernetes)
      - [Add Annotation To Build](#add-annotation-to-build)
  - [Understanding how it works](#understanding-how-it-works)
    - [Template vs Node](#template-vs-node)
      - [Maven Node](#maven-node)
      - [Docker Node](#docker-node)
      - [Clients Node](#clients-node)
      - [Release Node](#release-node)
    - [Mixing and matching](#mixing-and-matching)
    - [Creating and using your own templates](#creating-and-using-your-own-templates)
      - [Using the Jenkins Administration Console](#using-the-jenkins-administration-console)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Fabric8 Pipeline Library

This git repository contains a library of reusable [Jenkins Pipeline](https://jenkins.io/doc/book/pipeline/) steps and functions that can be used in your `Jenkinsfile` to help improve your Continuous Delivery pipeline.

<p align="center">
  <a href="http://fabric8.io/guide/cdelivery.html">
  	<img src="https://raw.githubusercontent.com/fabric8io/fabric8/master/docs/images/cover/cover_small.png" alt="fabric8 logo"/>
  </a>
</p>

The idea is to try promote sharing of scripts across projects where it makes sense.

## How to use this library

This library is intended to be used with fabric8's Jenkins image that is deployed as part of the [fabric8 platform](https://fabric8.io).

To use the functions in this library just add the following to the top of your `Jenkinsfile`:

```groovy
@Library('github.com/fabric8io/fabric8-pipeline-library@master') _
```

That will use the master branch of this library. You can if you wish pick a specific [tag](https://github.com/fabric8io/fabric8-pipeline-library/tags) or [commit SHA](https://github.com/fabric8io/fabric8-pipeline-library/commits/master) of this repository too.

### Making changes

Feel free to reuse a version of this library as is. However if you want to make changes, please `fork` this repository and change it in your own fork!

Then just refer to your fork in the `@Library()` annotation as shown above.

If you do make local changes we'd love a `Pull Request` back though! We love contributions and pull requests!


### Requirements

These flows make use of the [Fabric8 DevOps Pipeline Steps](https://github.com/fabric8io/fabric8-jenkins-workflow-steps) and [kubernetes-plugin](https://github.com/jenkinsci/kubernetes-plugin) which help when working with [Fabric8 DevOps](http://fabric8.io/guide/cdelivery.html) in particular for clean integration with the [Hubot chat bot](https://hubot.github.com/) and human approval of staging, promotion and releasing.

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
#### Get Deployment Resources

- returns a default OpenShift or Kubernetes YAML that can be used by kubernetes-workflow apply step
- returns a service, deployment / deployment config YAML using sensible defaults
- can be used in conjunction with [kubernetesApply](https://github.com/jenkinsci/kubernetes-pipeline-plugin/blob/master/devops-steps/readme.md#applying-kubernetes-configuration)
```groovy
    node {
        def resources = getDeploymentResources {
          port = 8080
          label = 'node'
          icon = 'https://cdn.rawgit.com/fabric8io/fabric8/dc05040/website/src/images/logos/nodejs.svg'
          version = '0.0.1'
        }

        kubernetesApply(file: resources, environment: 'my-cool-app-staging', registry: 'myexternalregistry.io:5000')
    }
```
#### Get Kubernetes JSON

__WARNING this function is deprecated.  Please change to use getDeploymentResources{}__

- returns a default OpenShift templates that gets translated into Kubernetes List when applied by kubernetes-workflow apply step and running on Kubernetes
- returns a service and replication controller JSON using sensible defaults
- can be used in conjunction with [kubernetesApply](https://github.com/jenkinsci/kubernetes-pipeline-plugin/blob/master/devops-steps/readme.md#applying-kubernetes-configuration)
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
- commits generated Helm charts to the fabric8 Helm repo
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

__NOTE__ in order for images to be found by the remote OpenShift instance it must be able to pull images from the staging docker registry.  Noting private networks and insecure-registry flags.

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

__NOTE__ in order for images to be found by the remote OpenShift instance it must be able to pull images from the staging docker registry.  Noting private networks and insecure-registry flags.    

```groovy
    node{
      deployRemoteKubernetes{
        url = kubernetesUrl
        defaultNamespace = 'default'
        stagingDockerRegistry = kubernetesStagingDockerRegistryUrl
      }
    }
```

#### Add Annotation To Build

Add an annotation to the matching openshift build

```groovy
    @Library('github.com/fabric8io/fabric8-pipeline-library@master')
    def dummy
    node{
        def utils = new io.fabric8.Utils()
        utils.addAnnotationToBuild('fabric8.io/foo', 'bar')
    }
```

## Understanding how it works

Most of the functions provided by this library are meant to run inside a Kubernetes or Openshift pod. Those pods are managed by the [kubernetes plugin](https://github.com/jenkinsci/kubernetes-plugin).
This library abstracts the pipeline capabilities of [kubernetes plugin](https://github.com/jenkinsci/kubernetes-plugin) so that it makes it easier to use. So for example when you need to use a pod with maven capabilities
instead of defining something like:

    podTemplate(label: 'maven-node', containers: [
        containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-8-alpine', ttyEnabled: true, command: 'cat')
      ],
      volumes: [secretVolume(secretName: 'shared-secrets', mountPath: '/etc/shared-secrets')]) {

        node('maven-node') {
            container(name: 'maven') {
                ...
            }
        }
      }

You can just use the mavenTemplate provided by this library:

    mavenTemplate(label: 'mylabel') {
        node('mylabel') {
            container(name: 'maven') {
              ...
            }
        }
    }

or for ease of use you can directly reference the mavenNode:

    mavenNode {
        container(name: 'maven') {
            ...
        }
    }

### Template vs Node

A template defines how the jenkins slave pod will look like, but the pod is not created until a node is requested.
When a node is requested the matching template will be selected and pod from the template will be created.

The library provides shortcut function both to nodes and templates. In most cases you will just need to use the node.
The only exception is when you need to mix and match (see [mixing and matching](#mixing-and-matching)).


The provided node / template pairs are the following:

* **maven**     Provides maven capabilities.
* **docker**    Provides access to the docker client and socket.
* **release**   Mounts release related secrets *(e.g. gpg keys, ssh keys etc)*.
* **clients**   Provides access to the kubernetes and openshift binaries.

#### Maven Node

Provides maven capabilities by adding a container with the maven image.
The container mounts the following volumes:

* Secret `jenkins-maven-settings` Add your maven configuration here.
* PersistentVolumeClaim `jenkins-mvn-local-repo` The maven local repository to use.

The maven node and template support limited customization through the following properties:

* **mavenImage** Select the maven docker image to use.

Example:

    mavenNode(mavenImage: 'maven:3.3.9-jdk-7') {
        container(name: 'maven') {
            sh 'mvn clean install'
        }
    }

#### Docker Node

Provides docker capabilities by adding a container with the docker binary.
The container mounts the following volumes:

* HostPathVolume `/var/run/docker.sock` The docker socket.

Host path mounts are not allowed everywhere, so use with caution.
Also note that the mount will be mounted to all containers in the pod.
This means that if we add a maven container to the pod, it will have docker capabilities.

The docker node and template support limited customization through the following properties:

* **dockerImage** Select the docker image to use.

Example:

    mavenNode(dockerImage: 'docker:1.11.2') {
        container(name: 'docker') {
            sh 'docker build -t myorg/myimage .'
        }
    }

#### Clients Node

Provides access to the `kubectl` and `oc` binaries by adding a container to the pod that provides them.
The container is configured exactly as the docker container provided by the dockerTemplate.

Example:

    clientsNode(clientsImage: 'fabric8/builder-clients:latest') {
        container(name: 'clients') {
            sh 'kubectl create -f ./target/classes/META-INF/kubernetes/kubernetes.yml'
        }
    }

#### Release Node

Provides docker capabilities by enriching the jenkins slave pod with the proper environment variables and volumes.

* Secret `jenkins-release-gpg` Add your maven configuration here.

Also the following environment variables will be available to all containers:

* SONATYPE_USERNAME
* SONATYPE_PASSWORD
* GPG_PASSPHRASE
* NEXUS_USERNAME
* NEXUS_PASSWORD

These variables will obtain their values from jenkins container (they will be copied).

Example:

    releaseTemplate {
        mavenNode {
        container(name: 'docker') {
            sh 'docker build -t myorg/myimage .'
        }
    }

### Mixing and matching

There are cases where we might need a more complex setup that may require
more than a single template. (e.g. a maven container that can run docker builds).

For this case you can combine add the docker template and the maven template together:

    dockerTemplate {
        mavenTemplate(label: 'maven-and-docker') {
            node('maven-and-docker') {
                 container(name: 'maven') {
                    sh 'mvn clean package fabric8:build fabric8:push'
                 }            
            }
        }
    }

The above is equivalent to:

    dockerTemplate {
        mavenNode(label: 'maven-and-docker') {
            container(name: 'maven') {
                sh 'mvn clean package fabric8:build fabric8:push'
            }            
        }
    }

In the example above we can add release capabilities too, by adding the releaseTemplate:


            dockerTemplate {
                releaseTemplate {
                    mavenNode(label: 'maven-and-docker') {
                        container(name: 'maven') {
                            sh """
                                mvn release:clean release:prepare
                                mvn clean release:perform
                            """
                        }            
                    }
                }
            }

### Creating and using your own templates

If the existing selection of templates is limiting you can also create your own templates.
Templates can be created either by using the Jenkins administration console or by using the groovy.

#### Using the Jenkins Administration Console

In the console choose `Manage Jenkins` -> `Configure System` and scroll down until you find the section `Cloud` -> `Kubernetes`.
There you can click to `Add Pod Template` to create your own using the wizard.

Then you can just instantiate the template by creating a node that references the label to the template:

            node('my-custom-template') {
            }

Note: You can use this template to mix and match too. For example you can combine your custom template with an existing one:

            mavenNode(inheritFrom: 'my-custom-template') {
            }
