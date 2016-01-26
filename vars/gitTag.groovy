def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    sh "git tag -fa v${config.releaseVersion} -m 'Release version ${config.releaseVersion}'"
    // also force push the tag incase release fails further along the pipeline
    sh "git push --force origin v${config.releaseVersion}"
}
