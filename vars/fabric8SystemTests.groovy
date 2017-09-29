#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def packageYAML = config.packageYAML

    def clusterName = env.JOB_NAME + env.BUILD_NUMBER
    echo "creating GKE cluster with name ${clusterName}"

    def machineType = 'n1-standard-2'
    def zone = 'europe-west1-b'
    def project = 'fabric8-1342'
    def numberOfNodes = '2'
    def diskSize = '50'
    def consoleURL
    def clusterInfo
    def msg
    def failed = false

    stage('Creating Cluster on Google Container Engine')
    container(name: 'clients') {
        try {
            sh 'gcloud auth activate-service-account --key-file /root/home/.gke/config.json'
            sh "gcloud container clusters create ${clusterName} --disk-size ${diskSize} --zone ${zone} --enable-cloud-logging --enable-cloud-monitoring --machine-type ${machineType} --num-nodes ${numberOfNodes}"
            sh "gcloud config set project ${project}"
            sh "gcloud config set compute/zone ${zone}"
            sh "gcloud config set container/cluster ${clusterName}"

            // this is needed to log into the new GKE cluster from our CD cluster
            sh "gcloud config set container/use_client_certificate True"
            sh "gcloud alpha container clusters get-credentials ${clusterName} -z ${zone}"
            sh """
cat <<EOF | kubectl create -f -
kind: StorageClass
apiVersion: storage.k8s.io/v1beta1
metadata:
  name: standard
provisioner: kubernetes.io/gce-pd
parameters:
  type: pd-standard
EOF
"""
            stage('Deploying fabric8')

            // get the latest gofabric8 and deploy onto our new GKE cluster
            sh "curl -sS https://get.fabric8.io/download.txt | bash"

            // optionally overrite the default yaml so we can test non released versions
            if (packageYAML != null){
                writeFile file: 'packageYAML', text: packageYAML
                sh " ~/.fabric8/bin/gofabric8 deploy --package packageYAML -y"
            } else {
                sh " ~/.fabric8/bin/gofabric8 deploy -y"
            }

            sh " ~/.fabric8/bin/gofabric8 wait-for jenkins gogs fabric8-forge nexus fabric8-docker-registry exposecontroller configmapcontroller"
            consoleURL = sh(returnStdout: true, script: 'TERM=dumb && ~/.fabric8/bin/gofabric8 service fabric8 -u').trim()
            clusterInfo = sh(returnStdout: true, script: 'TERM=dumb && kubectl cluster-info').trim()
        } catch (e) {
            echo "ERROR creating new fabric8 cluster ${e}"
            sh "gcloud container clusters delete ${clusterName} -q"
            error "${e}"
        }

        hubotSend message: "fabric8 deployed and system tests about to start, to follow along visit ${consoleURL}", failOnError: false

        try {
            stage('Running system tests')
            sh "kubectl config set-context `kubectl config current-context` --namespace=default"
            sh 'kubectl -n default apply -f https://gist.githubusercontent.com/rawlingsj/1dcadcd4c68533af252a0efbe27c5be5/raw/159da9303385933e5140031999504f33acf6183f/gistfile1.txt'

            waitUntil {
                try {
                    echo 'Waiting until system-test-job pod is running'
                    sh 'kubectl -n default get pods | grep system-test-job | grep Running'
                } catch (e) {
                    echo "${e}"
                    return false
                }
                return true
            }

            def podName = sh(returnStdout: true, script: "kubectl -n default get pods | grep system-test-job | cut -f 1 -d ' '").trim()

            sh "kubectl -n default logs -f ${podName}"

            def rs = sh(returnStdout: true, script: "kubectl -n default logs ${podName} --tail=1").trim()

            if (rs != null && rs.equals('SYSTEM TESTS FAILED')) {
                failed = true
                def logs = sh(returnStdout: true, script: "kubectl -n default logs ${podName} --tail=40").trim()
                msg = getFailedMessage(clusterName, logs, consoleURL)
            } else {
                msg = getSuccessMessage(clusterName)
            }
        } catch (e) {
            failed = true
            msg = "ERROR: ${e}"
        }

        // if there was a failure then notify and give the option to keep the cluster around
        try {
            stage('Notification')
            if (failed) {
                hubotApprove message: msg, failOnError: false
            } else {
                hubotSend message: msg, failOnError: false
            }

        } catch (e1) {
            echo 'Keeping the cluster running'
            error "${e1}"
        }

        stage('Tearing down cluster')
        // if there were no errors then tear the cluster down
        echo 'Tearing down staging cluster and continuing'
        sh "gcloud container clusters delete ${clusterName} -q"
    }
}

def getFailedMessage(clusterName, logs, consoleURL){
    return """
system tests failed for ${clusterName}

${logs}

take a look and see what's going on ${consoleURL}

shall we tear down the cluster?
"""
}

def getSuccessMessage(clusterName){
    return """"
${clusterName} deployed, system tests passed

we're tearing down the cluster..
"""
}
