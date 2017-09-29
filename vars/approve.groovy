#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def timeoutTime= config.timeoutTime ?: 24
    def proceedMessage = """Would you like to promote version ${config.version} to the next environment?
"""

    hubotApprove message: proceedMessage, failOnError: false
    def id = approveRequestedEvent(app: "${env.JOB_NAME}", environment: config.environment)

    try {
        timeout(time: timeoutTime, unit: 'HOURS') {
            input id: 'Proceed', message: "\n${proceedMessage}"
        }
    } catch (err) {
        approveReceivedEvent(id: id, approved: false)
        throw err
    }
    approveReceivedEvent(id: id, approved: true)
}
