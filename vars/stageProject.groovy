#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    def repoId
    def releaseVersion
    def extraStageImages = config.extraImagesToStage ?: []
    def extraSetVersionArgs = config.setVersionExtraArgs ?: ""
    def containerName = config.containerName ?: 'maven'

    container(name: containerName) {

        flow.setupGitSSH()
        flow.setupGPG()

        sh "git remote set-url origin git@github.com:${config.project}.git"

        def currentVersion = flow.getProjectVersion()

        flow.setupWorkspaceForRelease(config.project, config.useGitTagForNextVersion, extraSetVersionArgs, currentVersion)

        repoId = flow.stageSonatypeRepo()
        releaseVersion = flow.getProjectVersion()

        // lets avoide the stash / unstash for now as we're not using helm ATM
        //stash excludes: '*/src/', includes: '**', name: "staged-${config.project}-${releaseVersion}".hashCode().toString()

        if (!config.useGitTagForNextVersion) {
            flow.updateGithub()
        }
    }

    if (config.extraImagesToStage != null) {
        stageExtraImages {
            images = extraStageImages
            tag = releaseVersion
        }
    }

    return [config.project, releaseVersion, repoId]
}
