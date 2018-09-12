package io.openshift

import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.job.WorkflowJob

class Utils {

    static String shWithOutput(script, String command) {
        return script.sh(
                script: command,
                returnStdout: true
        ).trim()
    }

    static def ocApply(script, resource, namespace) {
        def buildNum = script.env.BUILD_NUMBER
        def kind = resource.kind.toLowerCase()
        def resourceFile = ".openshiftio/.tmp-${namespace}-${buildNum}-${kind}.yaml"
        script.writeYaml file: resourceFile, data: resource
        script.sh """
            oc apply -f ${resourceFile} -n ${namespace}
            rm -f ${resourceFile}
        """
    }

    static String usersNamespace() {
        def ns = currentNamespace()
        if (ns.endsWith("-jenkins")) {
            return ns.substring(0, ns.lastIndexOf("-jenkins"))
        }
        return ns
    }

    static String currentNamespace() {
        OpenShiftClient oc = new DefaultOpenShiftClient()
        return oc.getNamespace()
    }

    static def addAnnotationToBuild(script, annotation, value) {
        def buildName = buildNameForJob(script.env.JOB_NAME, script.env.BUILD_NUMBER)
        if (!isValidBuildName(buildName)) {
            script.error "No matching openshift build with name ${buildName} found"
        }

        script.echo "Adding annotation '${annotation}: ${value}' to Build ${buildName}"
        OpenShiftClient oc = new DefaultOpenShiftClient()
        oc.builds().inNamespace(usersNamespace()).withName(buildName)
            .edit().editMetadata().addToAnnotations(annotation, value).endMetadata()
            .done()
    }

    static def buildNameForJob(String jobName, String buildNumber) {
        def activeInstance = Jenkins.getInstance()
        def job = (WorkflowJob) activeInstance.getItemByFullName(jobName)
        def run = job.getBuildByNumber(Integer.parseInt(buildNumber))
        def clazz = Thread.currentThread().getContextClassLoader().loadClass("io.fabric8.jenkins.openshiftsync.BuildCause")
        def cause = run.getCause(clazz)
        if (cause != null) {
            return cause.name
        }
        return null
    }

    static boolean isValidBuildName(String buildName) {
        OpenShiftClient oc = new DefaultOpenShiftClient()
        def build = oc.builds().inNamespace(usersNamespace()).withName(buildName).get()
        return build != null
    }
}
