#!/usr/bin/groovy

def call(Map args = [:], Closure body) {

    def targetBranch = args.branch ?: /^PR-\d+$/

    if (env.BRANCH_NAME.trim() ==~ targetBranch) {
        body()
    }
}