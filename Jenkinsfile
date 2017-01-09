#!/usr/bin/groovy
import io.fabric8.Fabric8Commands

@Library('github.com/fabric8io/fabric8-pipeline-library@master')
def dummy
clientsNode {
    ws ('pipelines'){
        git 'https://github.com/fabric8io/fabric8-pipeline-library.git'

        def pipeline = load 'release.groovy'

        stage 'Tag'
        pipeline.tagDownstreamRepos()
    }
}