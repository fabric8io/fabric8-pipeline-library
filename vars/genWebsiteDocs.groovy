#!/usr/bin/groovy

/*
 *   Copyright (c) 2016 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version
 *   2.0 (the "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *   implied.  See the License for the specific language governing
 *   permissions and limitations under the License.
 */

/**
 * @author kameshs
 */

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def project = config.project[0]
    def artifactId = config.artifactId
    def docVersion = config.project[1]
    def docgenScript = config.docgenScript ?: null
    def gitUser = config.gitUser ?: "fabric8-release"
    def gitEmail = config.gitEmail ?: "fabric8-admin@googlegroups.com"

    //Array of Maven Profiles
    def profiles = config.profiles ?: null

    def gitRepoUrl = "git@github.com:${project}.git"
    def containerName = config.containerName ?: 'maven'

    container(name: containerName) {

        checkout scm: [$class          : 'GitSCM',
                       useRemoteConfigs: [[url: gitRepoUrl]],
                       branches        : [[name: "refs/tags/v${docVersion}"]]],
                changelog: false, poll: false

        if (docgenScript == null) {
            //if no profiles are passed we will try running doc-html, doc-pdf
            if (profiles == null) {
                sh('mvn -Pdoc-html && mvn -Pdoc-pdf')
            } else {
                def mvnCmd = 'mvn -P' + profiles.join(" && mvn -P")
                sh("mvn ${mvnCmd}")
            }

            def refGHPages = sh(script: 'git rev-parse --abbrev-ref --glob=\'refs/remotes/origin/gh-pages*\'',
                    returnStdout: true).toString().trim()

            if (refGHPages?.trim()) {

                def workspace = pwd()

                def ghPagesDir = "gh-pages"

                sh "mkdir -p ${workspace}/${ghPagesDir}"

                sh "git clone -b gh-pages  ${gitRepoUrl} ${workspace}/${ghPagesDir}"
                sh "cp -rv ${workspace}/target/generated-docs/* ${workspace}/${ghPagesDir}/"
                sh "mv ${workspace}/${ghPagesDir}/index.pdf  ${workspace}/${ghPagesDir}/${artifactId}.pdf"
                sh "cd ${workspace}/${ghPagesDir} && git config user.email ${gitEmail} " +
                        " && git config user.name ${gitUser} " +
                        " && git add --ignore-errors * || true " +
                        " && git commit -m 'generated documentation'" +
                        " && git push origin gh-pages"

            } else {

                sh 'git checkout -b gh-pages'

                sh 'cp -rv target/generated-docs/* .'

                sh "git config user.email ${gitEmail} && git config user.name ${gitUser} && " +
                        "(git add --ignore-errors * || true ) && git commit -m 'generated documentation' " +
                        "&& git push origin gh-pages"
            }

        } else {
            sh "${docgenScript}"
        }

    }
}
