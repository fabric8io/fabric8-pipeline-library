#!/usr/bin/groovy
import org.apache.maven.model.Build
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginExecution
import org.apache.maven.model.Profile
import groovy.transform.Field

@Field final String FMP_STABLE_VERSION = "3.5.38"

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    def utils = new io.fabric8.Utils()
    def autoUpdateFMP = config.autoUpdateFabric8Plugin ?: true
    def skipTests = config.skipTests ?: false

    sh "git checkout -b ${env.JOB_NAME}-${config.version}"

    if (autoUpdateFMP) {
        patchFMPVersion()
    }
    sh "mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -U -DnewVersion=${config.version}"
    sh "mvn clean -B -e -U deploy -Dmaven.test.skip=${skipTests} -P openshift"


    junitResults(body);

    def buildName = ""
    try {
        buildName = utils.getValidOpenShiftBuildName()
    } catch (err) {
        echo "Failed to find buildName due to: ${err}"
    }

    if (buildName != null && !buildName.isEmpty()) {
        def buildUrl = "${env.BUILD_URL}"
        if (!buildUrl.isEmpty()) {
            utils.addAnnotationToBuild('fabric8.io/jenkins.testReportUrl', "${buildUrl}testReport")
        }
        def changeUrl = env.CHANGE_URL
        if (changeUrl != null && !changeUrl.isEmpty()) {
            utils.addAnnotationToBuild('fabric8.io/jenkins.changeUrl', changeUrl)
        }

        bayesianScanner(body);
    }

    sonarQubeScanner(body);

    def s2iMode = utils.supportsOpenShiftS2I()
    echo "s2i mode: ${s2iMode}"

    if (!s2iMode) {
        def registry = utils.getDockerRegistry()
        def m = readMavenPom file: 'pom.xml'
        def groupId = m.groupId.split('\\.')
        def user = groupId[groupId.size() - 1].trim()
        def artifactId = m.artifactId

        sh "docker tag ${user}/${artifactId}:${config.version} ${registry}/${user}/${artifactId}:${config.version}"
        if (!flow.isSingleNode()) {
            echo 'Running on a single node, skipping docker push as not needed'
            retry(5) {
                sh "docker tag ${user}/${artifactId}:${config.version} ${registry}/${user}/${artifactId}:${config.version}"
                sh "docker push ${registry}/${user}/${artifactId}:${config.version}"
            }
        }
    }

    contentRepository(body);
}

def patchFMPVersion() {
    def pomModel = readMavenPom file: 'pom.xml'

    if (!pomModel.profiles) {
        return false
    }

    def updatedPlugin = false
    for(profile in pomModel.profiles) {
        if(!profile.id.equalsIgnoreCase("openshift") || !profile.build.plugins) {
            continue
        }

        for(plugin in profile.build.plugins) {
            if(plugin.artifactId != "fabric8-maven-plugin") {
                continue
            }

            // check FMP plugin definition has '<version>' property
            // else update FMP plugin definition <version> property according to <parent> version
            if(plugin.version) {
                if (compareVersions(plugin.version, FMP_STABLE_VERSION) < 0) {
                    println "patching maven plugin for fabric8-maven-plugin v" + FMP_STABLE_VERSION
                    plugin.version = FMP_STABLE_VERSION
                    updatedPlugin = true
                } else {
                    return false
                }
            } else {
                updatedPlugin = patchPluginBasedOnParent(plugin, pomModel.parent)
            }
        }
    }

    // if there is no FMP plugin found in pom.xml then try to add FMP definition based on parent module
    if(!updatedPlugin) {
        updatedPlugin = patchProfileBasedOnParent(pomModel)
    }

    if (updatedPlugin) {
        writeMavenPom model: pomModel
    }

    return updatedPlugin
}

/*
* if v1 is greater than v2 returns +ve number
* if v2 is greater than v1 returns -ve number
* if v1 is equal to v2 return 0
*/
def compareVersions(v1, v2) {
    // not using def (v1Parts, v2Parts) = .. because UnsupportedOperationException: multiple assignments not supported
    def versions = partitionVersions(v1, v2)
    def v1Parts = versions[0]
    def v2Parts = versions[1]
    def maxParts = v1Parts.size()
    for (int i=0; i < maxParts; i++) {
        if (v1Parts[i] != v2Parts[i]) {
            return v1Parts[i] - v2Parts[i]
        }
    }

    return 0
}

/*
* due to Jenkins sandbox [0] * num is restricted to use
*/
def zeroFill(v, count) {
    (0..count).each {
        v << 0
    }
}

/**
 * given versions strings v1 and v2 returns versions partitioned into
 * an array of strings of equal length. e.g.
 * "1.2", "3.4.5" returns [1, 2, 0], [3, 4, 5]
 * "1.2.3-5", "3.4.5" returns [1, 2, 3, 15], [3, 4, 5]
 */
def partitionVersions(v1, v2) {
    def delim = '[\\.-]'
    def v1Parts = v1.split(delim).collect { Integer.valueOf(it) }
    def v2Parts = v2.split(delim).collect { Integer.valueOf(it) }

    // make the versions equal by zero filling the array
    def maxParts = Math.max(v1Parts.size(), v2Parts.size())
    zeroFill(v1Parts, maxParts - v1Parts.size() )
    zeroFill(v2Parts, maxParts - v2Parts.size() )

    return [v1Parts, v2Parts]
}

def patchPluginBasedOnParent(plugin, parent) {
    if (!parent) {
        return false
    }

    if (parent.artifactId == "booster-parent" && compareVersions(parent.version, "20") < 0) {
        println "patching maven plugin for booster-parent parent v20"
        plugin.version = FMP_STABLE_VERSION
        return true
    }

    if (parent.artifactId == "spring-boot-booster-parent" && compareVersions(parent.version, "1.5.10-15") < 0) {
        println "patching maven plugin for spring-boot-booster-parent v1.5.10-15"
        plugin.version = FMP_STABLE_VERSION
        return true;
    }

    return false;
}

def patchProfileBasedOnParent(pomModel) {
    def parent = pomModel.parent

    if (!parent) {
        return false
    }

    if (parent.artifactId == "booster-parent" && compareVersions(parent.version, "20") < 0) {
        println "patching maven plugin for booster-parent parent v20"
        addFMPDefinition(pomModel, FMP_STABLE_VERSION)
        return true
    }

    if (parent.artifactId == "spring-boot-booster-parent" && compareVersions(parent.version, "1.5.10-15") < 0) {
        println "patching maven plugin for spring-boot-booster-parent v1.5.10-15"
        addFMPDefinition(pomModel, FMP_STABLE_VERSION)
        return true;
    }

    return false
}

def addFMPDefinition(pomModel, fmpVersion) {
    final List<PluginExecution> executions = new ArrayList<>()
    PluginExecution aPluginExecution = new PluginExecution()
    aPluginExecution.setId("fmp")
    aPluginExecution.addGoal("resource")
    aPluginExecution.addGoal("build")
    executions.add(aPluginExecution)

    final Plugin fabric8Plugin = new Plugin()
    fabric8Plugin.setGroupId("io.fabric8")
    fabric8Plugin.setArtifactId("fabric8-maven-plugin")
    fabric8Plugin.setVersion(fmpVersion)
    fabric8Plugin.setExecutions(executions)

    Build build = new Build()
    build.getPlugins().add(fabric8Plugin)

    Profile fmpProfile = new Profile()
    fmpProfile.setId("openshift")
    fmpProfile.setBuild(build)

    pomModel.profiles += fmpProfile
}
