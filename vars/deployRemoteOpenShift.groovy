#!/usr/bin/groovy
def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def flow = new io.fabric8.Fabric8Commands()

  kubernetes.pod('buildpod').withImage('fabric8/builder-clients')
  .withSecret('remote-openshift-token','/root/.oc/')
  .withPrivileged(true)
  .inside {

    sh "oc login ${config.url} --token=\$(cat /root/.oc/token) --insecure-skip-tls-verify=true"
    try{
      sh 'oc delete project fabric8-test'
      waitUntil{
        // wait until the project has been deleted
        try{
          sh "oc get projects | cut -f 1 -d ' ' | grep fabric8-test"
          echo 'openshift project fabric8-test still exists, waiting until deleted'
        } catch (err) {
          echo "${err}"
          // project doesnt exist anymore so continue
          return true
        }
        return false
      }
    } catch (err) {
      // dont need to worry if there's no existing test environment to delete
    }
    sh 'oc new-project fabric8-test'
    sh "gofabric8 deploy -y --docker-registry ${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT} --api-server ${config.url} --domain ${config.domain} --maven-repo https://oss.sonatype.org/content/repositories/staging/"

  }
}
