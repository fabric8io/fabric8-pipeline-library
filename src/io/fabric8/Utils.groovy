#!/usr/bin/groovy
package io.fabric8

import com.cloudbees.groovy.cps.NonCPS
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.job.WorkflowJob

@NonCPS
String environmentNamespace(String environment) {
  try {
    def answer = io.fabric8.kubernetes.api.environments.Environments.namespaceForEnvironment(environment)
    if (answer) {
      return answer;
    }
  } catch (e) {
    echo "WARNING: Failed to invoke Environments.namespaceForEnvironment(environment) probably due to API whitelisting: ${e}"
    e.printStackTrace()
  }
  String ns = getNamespace()
  if (ns.endsWith("-jenkins")){
    ns = ns.substring(0, ns.lastIndexOf("-jenkins"))
  }
  return ns + "-${environment.toLowerCase()}"
}

/**
 * Loads the environments in the default user namespace
 */
@NonCPS
def environments() {
  try {
    return io.fabric8.kubernetes.api.environments.Environments.load()
  } catch (e) {
    echo "WARNING: Failed to invoke Environments.load() probably due to API whitelisting: ${e}"
    e.printStackTrace()
  }
  // TODO can't do this in old jenkins which don't have this class yet
  // return new io.fabric8.kubernetes.api.environments.Environments(getNamespace(), new HashMap())
  return null
}

/**
 * Loads the environments from the given namespace
 */
@NonCPS
def environments(String namespace) {
  try {
    return io.fabric8.kubernetes.api.environments.Environments.load(namespace)
  } catch (e) {
    echo "WARNING: Failed to invoke Environments.load(namespace) probably due to API whitelisting: ${e}"
    e.printStackTrace()
  }
  // TODO can't do this in old jenkins which don't have this class yet
  // return new io.fabric8.kubernetes.api.environments.Environments(namespace, new HashMap())
  return null
}


/**
 * Loads the environments from the user namespace
 */
@NonCPS
def pipelineConfiguration() {
  try {
    return io.fabric8.kubernetes.api.pipelines.PipelineConfiguration.loadPipelineConfiguration()
  } catch (e) {
    echo "WARNING: Failed to invoke Environments.loadPipelineConfiguration() probably due to API whitelisting: ${e}"
    e.printStackTrace()
  }
  // TODO can't do this in old jenkins which don't have this class yet
  // return new io.fabric8.kubernetes.api.pipelines.PipelineConfiguration()
  return null
}


/**
 * Loads the environments from the given namespace
 */
@NonCPS
def pipelineConfiguration(String namespace) {
  try {
    return io.fabric8.kubernetes.api.pipelines.PipelineConfiguration.loadPipelineConfiguration(namespace)
  } catch (e) {
    echo "WARNING: Failed to invoke PipelineConfiguration.loadPipelineConfiguration(namespace) probably due to API whitelisting: ${e}"
    e.printStackTrace()
  }
  // TODO can't do this in old jenkins which don't have this class yet
  //return new io.fabric8.kubernetes.api.pipelines.PipelineConfiguration()
  return null
}

/**
 * Returns true if the integration tests should be disabled
 */
@NonCPS
boolean isDisabledITests() {
  boolean answer = false
  try {
    def config = pipelineConfiguration()
    echo "Loaded PipelineConfiguration ${config}"

    if (isCD()) {
      answer = config.isDisableITestsCD()
    } else if (isCI()) {
      answer = config.isDisableITestsCI()
    }
  } catch (e) {
    echo "WARNING: Failed to find the flag on the PipelineConfiguration object - probably due to the jenkins plugin `kubernetes-pipeline-plugin` version: ${e}"
    e.printStackTrace()
  }

  // TODO lets just disable ITests for now until this issue is fixed:
  echo "Due to this issue: https://github.com/openshiftio/booster-common/issues/8 we are temporary disabling integration tests OOTB"
  answer = true
  return answer;
}

/**
 * Returns true if we should use S2I to build docker images
 */
@NonCPS
def isUseOpenShiftS2IForBuilds() {
    return !isUseDockerSocket()
}

/**
 * Returns true if the current cluster can support S2I
 */
@NonCPS
def supportsOpenShiftS2I() {
    DefaultOpenShiftClient client = new DefaultOpenShiftClient();
    if (client.isAdaptable(OpenShiftClient.class)) {
        try {
            if (client.supportsOpenShiftAPIGroup("image.openshift.io")) {
                return true
            }
        } catch (e) {
          echo "WARNING: Failed to use the OpenShiftClient.supportsOpenShiftAPIGroup() API: ${e}"
          e.printStackTrace()
          return true
        }
    }
    return false
}

/**
 * Returns true if we should mount the docker socket for docker builds
 */
@NonCPS
boolean isUseDockerSocket() {
  boolean answer = false
  try {
    def config = pipelineConfiguration()
    echo "Loaded PipelineConfiguration ${config}"
    def flag = config.getUseDockerSocketFlag()
    if (flag != null) {
      echo "Loaded the useDockerSocket flag ${flag}"
      return flag ? true : false
    }
  } catch (e) {
    echo "WARNING: Failed to find the getUseDockerSocketFlag() flag on the PipelineConfiguration object - probably due to the jenkins plugin `kubernetes-pipeline-plugin` version: ${e}"
    e.printStackTrace()
  }
  return supportsOpenShiftS2I() ? false : true;
}

@NonCPS
String getDockerRegistry() {

    def externalDockerRegistryURL = getUsersPipelineConfig('external-docker-registry-url')
    if (externalDockerRegistryURL){
      return externalDockerRegistryURL
    }

    // fall back to the old < 4.x when the registry was in the same namespace
    def registryHost = env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST
    def registryPort = env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT
    if (!registryHost || !registryPort){
       error "No external-docker-registry found in Jenkins configmap or no FABRIC8_DOCKER_REGISTRY_SERVICE_HOST FABRIC8_DOCKER_REGISTRY_SERVICE_PORT environment variables"
    }
    return registryHost + ':' + registryPort
}

@NonCPS
String getUsersPipelineConfig(k) {

    // first lets check if we have the new pipeliens configmap in the users home namespace
    KubernetesClient client = new DefaultKubernetesClient()
    def ns = getUsersNamespace()
    def r = client.configMaps().inNamespace(ns).withName('fabric8-pipelines').get()
    if (!r){
      error "no fabric8-pipelines configmap found in namespace ${ns}"
    }
    def d = r.getData()
    echo "looking for key ${k} in ${ns}/fabric8-pipelines configmap"
    def v = d[k]
    return v
}

// returns a map of the configmap data in a given namepspace
@NonCPS
String getConfigMap(ns, cm, key) {

    // first lets check if we have the new pipeliens configmap in the users home namespace
    KubernetesClient client = new DefaultKubernetesClient()
    if (!ns){
      ns = getNamespace()
    }

    def r = client.configMaps().inNamespace(ns).withName(cm).get()
    if (!r){
      error "no ${cm} configmap found in namespace ${ns}"
    }
    if (key){
      return r.getData()[key]
    }
    return r.getData()
}

@NonCPS
private Map<String, String> parseConfigMapData(final String input) {
    final Map<String, String> map = new HashMap<String, String>();
    for (String pair : input.split("\n")) {
        String[] kv = pair.split(":");
        map.put(kv[0].trim(), kv[1].trim());
    }
    return map;
}

@NonCPS
String getNamespace() {
  KubernetesClient client = new DefaultKubernetesClient()
  return client.getNamespace()
}

@NonCPS
def getImageStreamSha(imageStreamName) {
  OpenShiftClient oc = new DefaultOpenShiftClient()
  return findTagSha(oc, imageStreamName, getNamespace())
}

// returns the tag sha from an imagestream
// original code came from the fabric8-maven-plugin
@NonCPS
def findTagSha(OpenShiftClient client, String imageStreamName, String namespace) {
  def currentImageStream = null
  for (int i = 0; i < 15; i++) {
    if (i > 0) {
      echo("Retrying to find tag on ImageStream ${imageStreamName}")
      try {
        Thread.sleep(1000)
      } catch (InterruptedException e) {
        echo("interrupted ${e}")
      }
    }
    currentImageStream = client.imageStreams().withName(imageStreamName).get()
    if (currentImageStream == null) {
      continue
    }
    def status = currentImageStream.getStatus()
    if (status == null) {
      continue
    }
    def tags = status.getTags()
    if (tags == null || tags.isEmpty()) {
      continue
    }
    // latest tag is the first
    TAG_EVENT_LIST:
    for (def list : tags) {
      def items = list.getItems()
      if (items == null) {
        continue TAG_EVENT_LIST
      }
      // latest item is the first
      for (def item : items) {
        def image = item.getImage()
        if (image != null && image != '') {
          echo("Found tag on ImageStream " + imageStreamName + " tag: " + image)
          return image
        }
      }
    }
  }
  // No image found, even after several retries:
  if (currentImageStream == null) {
    error ("Could not find a current ImageStream with name " + imageStreamName + " in namespace " + namespace)
  } else {
    error ("Could not find a tag in the ImageStream " + imageStreamName)
  }
}

@NonCPS
def addAnnotationToBuild(annotation, value) {
  def flow = new Fabric8Commands()
  if (flow.isOpenShift()) {
    def buildName = getValidOpenShiftBuildName()
    echo "Adding annotation '${annotation}: ${value}' to Build ${buildName}"
    OpenShiftClient oClient = new DefaultOpenShiftClient()
    def usersNamespace = getUsersNamespace()
    echo "looking for ${buildName} in namespace ${usersNamespace}"
    oClient.builds().inNamespace(usersNamespace).withName(buildName).edit().editMetadata().addToAnnotations(annotation, value).endMetadata().done()
  } else {
    echo "Not running on openshift so skip adding annotation ${annotation}: value"
  }
}

@NonCPS
def getUsersNamespace(){
    def usersNamespace = getNamespace()
    if (usersNamespace.endsWith("-jenkins")){
      usersNamespace = usersNamespace.substring(0, usersNamespace.lastIndexOf("-jenkins"))
    }
    return usersNamespace
}

def getBranch(){
  def branch = env.BRANCH_NAME
  if (!branch){
    try {
      branch = sh(script: 'git symbolic-ref --short HEAD', returnStdout: true).toString().trim()
    } catch (err){
      echo('Unable to get git branch and in a detached HEAD. You may need to select Pipeline additional behaviour and \'Check out to specific local branch\'')
      return null
    }
  }
  echo "Using branch ${branch}" 
  return branch
}

def isCI(){
  def branch = getBranch()
  if(branch && branch.startsWith('PR-')){
    return true
  }
  // if we can't get the branch assume we're not in a CI pipeline as that would be a PR branch
  return false
}

def isCD(){
  def branch = getBranch()
  if(!branch || branch.equals('master')){
    return true
  }
  // if we can't get the branch assume we're not in a CI pipeline as that would be a PR branch
  return false
}

def addPipelineAnnotationToBuild(t){
    addAnnotationToBuild('fabric8.io/pipeline.type', t)
}

def getLatestVersionFromTag(){
  sh 'git fetch --tags'
  sh 'git config versionsort.prereleaseSuffix -RC'
  sh 'git config versionsort.prereleaseSuffix -M'

  // if the repo has no tags this command will fail
  def version = sh(script: 'git tag --sort version:refname | tail -1', returnStdout: true).toString().trim()

  if (version == null || version.size() == 0){
    error 'no release tag found'
  }
  return version.startsWith("v") ? version.substring(1) : version
}

@NonCPS
def isValidBuildName(buildName){
  def flow = new Fabric8Commands()
  if (flow.isOpenShift()) {
    echo "Looking for matching Build ${buildName}"
    OpenShiftClient oClient = new DefaultOpenShiftClient()
    def usersNamespace = getUsersNamespace()
    def build = oClient.builds().inNamespace(usersNamespace).withName(buildName).get()
    if (build){
      return true
    }
    return false
  } else {
    error "Not running on openshift so cannot lookup build names"
  }
}

@NonCPS
def getValidOpenShiftBuildName(){

  def buildName = getOpenShiftBuildName()
  if (isValidBuildName(buildName)){
    return buildName
  } else {
    error "No matching openshift build with name ${buildName} found"
  }
}

def replacePackageVersion(packageLocation, pair){

  def property = pair[0]
  def version = pair[1]

  sh "sed -i -r 's/\"${property}\": \"[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2})?(.[0-9][0-9]{0,2})?(-development)?\"/\"${property}\": \"${version}\"/g' ${packageLocation}"

}

def replacePackageVersions(packageLocation, replaceVersions){
  for(int i = 0; i < replaceVersions.size(); i++){
    replacePackageVersion(packageLocation, replaceVersions[i])
  }
}


def getExistingPR(project, pair){
    def property = pair[0]
    def version = pair[1]

    def flow = new Fabric8Commands()
    def githubToken = flow.getGitHubToken()
    def apiUrl = new URL("https://api.github.com/repos/${project}/pulls")
    def rs = restGetURL{
        authString = githubToken
        url = apiUrl
    }

    if (rs == null || rs.isEmpty()){
      return null
    }
    for(int i = 0; i < rs.size(); i++){
      def pr = rs[i]
      echo "checking PR ${pr.number}"
      if (pr.state == 'open' && pr.title.contains("fix(version): update ${property} to ${version}")){
        echo 'matched'
        return pr.number
      }
    }
    return null
}

def getOpenPRs(project){

  def openPRs = []
  def flow = new Fabric8Commands()
  def githubToken = flow.getGitHubToken()
  def apiUrl = new URL("https://api.github.com/repos/${project}/pulls")
  def rs = restGetURL{
    authString = githubToken
    url = apiUrl
  }

  if (rs == null || rs.isEmpty()){
    return false
  }
  for(int i = 0; i < rs.size(); i++){
    def pr = rs[i]

    if (pr.state == 'open'){
      openPRs << String.valueOf(pr.number)
    }
  }
  return openPRs
}

def getDownstreamProjectOverrides(project, id, downstreamProject, botName = '@fabric8cd'){

  if (!downstreamProject){
    error 'no downstreamProjects provided'
  }
  def flow = new Fabric8Commands()
  def comments = flow.getIssueComments(project, id)
  // start by looking at the most recent commments and work back
  Collections.reverse(comments)
  for (comment in comments) {
    echo "Found PR comment ${comment.body}"
    def text = comment.body.trim()
    def match = 'CI downstream projects'
    if (text.startsWith(botName)){
      if (text.contains(match)){
        def result = text.substring(text.indexOf("[") + 1, text.indexOf("]"))
        if (!result){
          echo 'no downstream projects found'
        }
        def list =  result.split(',')
        for (repos in list) {
          if (!repos.contains('=')){
            error 'no override project found in the form organisation=foo'
          }
          def overrides =  repos.split('=')
          if (downstreamProject == overrides[0].trim()){
            "matched and returning ${overrides[1].trim()}"
            return overrides[1].trim()
          }
        }
      }
    }
  }
}

def hasPRComment(project, id, match){
  def flow = new Fabric8Commands()
  def comments = flow.getIssueComments(project, id)
  // start by looking at the most recent commments and work back
  Collections.reverse(comments)
  for (comment in comments) {
    echo "Found PR comment ${comment.body}"
    def text = comment.body.trim()
    if (text.equalsIgnoreCase(match)){
      return true
    }
  }
  return false
}

def getDownstreamProjectOverrides(downstreamProject, botName = '@fabric8cd'){

  def flow = new Fabric8Commands()

  def id = env.CHANGE_ID
  if (!id){
    error 'no env.CHANGE_ID / pull request id found'
  }

  def project = getRepoName()

  return getDownstreamProjectOverrides(project, id, downstreamProject, botName = '@fabric8cd')
}


def isSkipCIDeploy(botName = '@fabric8cd'){
  def id = env.CHANGE_ID
  if (!id){
    error 'no env.CHANGE_ID / pull request id found'
  }

  def flow = new Fabric8Commands()
  def project = getRepoName()

  def comments = flow.getIssueComments(project, id)
  // start by looking at the most recent commments and work back
  Collections.reverse(comments)
  for (comment in comments) {
    echo comment.body
    def text = comment.body.trim()
    def skipTrue = 'CI skip deploy=true'
    def skipFalse = 'CI skip deploy=false'
    if (text.startsWith(botName)){
      if (text.contains(skipTrue)){
        return true
      } else if (text.contains(skipFalse)){
        return false
      }
    }
  }
}

// helper to get the repo name from the job name when using org + branch github plugins
def getRepoName(){

  def jobName = env.JOB_NAME

  // job name from the org plugin
  if (jobName.count('/') > 1){
    return jobName.substring(jobName.indexOf('/')+1, jobName.lastIndexOf('/'))
  }
  // job name from the branch plugin
  if (jobName.count('/') > 0){
    return jobName.substring(0, jobName.lastIndexOf('/'))
  }
  // normal job name
  return jobName
}

@NonCPS
def getOpenShiftBuildName(){
  def activeInstance = Jenkins.getActiveInstance()
  def  job = (WorkflowJob) activeInstance.getItemByFullName(env.JOB_NAME)
  def run = job.getBuildByNumber(Integer.parseInt(env.BUILD_NUMBER))
  def flow = new Fabric8Commands()
  if (flow.isOpenShift()){
    def clazz = Thread.currentThread().getContextClassLoader().loadClass("io.fabric8.jenkins.openshiftsync.BuildCause")
    def cause = run.getCause(clazz)
    if (cause != null) {
      return cause.name
    }
  }
  return null
}

def isKubernetesPluginVersion013(){
    def isNewVersion = false

    try{
      def object = new org.csanchez.jenkins.plugins.kubernetes.PodAnnotation('dummy','dummy')
      def objPackage = object.getClass().getPackage()
      def version = objPackage.getImplementationVersion()
      // we could be using a custom built jar so remove any -SNAPSHOT from the version
      def v = Double.parseDouble(version.replaceAll("-SNAPSHOT",""));

      if (v >= 0.13) {
        isNewVersion = true
      }
    } catch (err) {
      echo "caught error when checking which kubernetes-plugin version we are using; defaulting to < 0.13: ${err}"
    }
    return isNewVersion
}

return this
