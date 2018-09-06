#!/usr/bin/groovy

def call(args = [:], Closure body) {
    def targetBranch = args.branch ?: 'master'
    if (env.BRANCH_NAME.equals(targetBranch)) {
        body()
    }
}
