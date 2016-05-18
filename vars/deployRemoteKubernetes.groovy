#!/usr/bin/groovy
def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  kubernetes.pod('buildpod').withImage('fabric8/builder-clients')
  .withSecret('remote-kubernetes-token','/root/.kc/')
  .withPrivileged(true)
  .inside {

    sh 'kubectl config set-credentials kube --username=\$(cat /root/.kc/user) --password=\$(cat /root/.kc/password)'
    sh "kubectl config set-cluster kube --insecure-skip-tls-verify=true --server=${config.url}"
    sh "kubectl config set-context kube --user=kube --namespace=${config.defaultNamespace} --cluster=kube"
    sh 'kubectl config use-context kube'

    try{
      sh 'kubectl delete namespace fabric8-test'
      waitUntil{
        // wait until the project has been deleted
        try{
          sh "kubectl get namespace | cut -f 1 -d ' ' | grep fabric8-test"
          echo 'kubectl namespace fabric8-test still exists, waiting until deleted'
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
    sh 'kubectl create namespace fabric8-test'
    sh "gofabric8 deploy -y --docker-registry ${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT} --api-server ${config.url} --maven-repo https://oss.sonatype.org/content/repositories/staging/"

  }
}
