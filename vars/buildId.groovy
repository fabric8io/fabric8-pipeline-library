#!/usr/bin/groovy

/**
 * Returns the id of the build, which consists of the job name, build number and an optional prefix.
 * @param prefix    The prefix to use, defaults in empty string.
 * @return
 */
def call(String prefix = '') {
    return  "${prefix}${env.JOB_NAME}_${env.BUILD_NUMBER}".replaceAll('-', '_').replaceAll('/', '_').replaceAll(' ', '_')
}