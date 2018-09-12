#!/usr/bin/groovy

def call(Map args = [:], body = null){
    if (args.commands == null && body == null) {
        error "Please specify either command or body; aborting ..."
        currentBuild.result = 'ABORTED'
        return
    }

    def spec = specForImage(args.image, args.version?: 'latest')
    def checkoutScm = args.checkout_scm ?: true
    pod(name: args.image, image: spec.image, shell: spec.shell) {
      if (checkoutScm) {
        checkout scm
      }

      if (args.commands != null) {
          sh args.commands
      }

      if (body != null) {
          body()
      }
    }
}

def specForImage(image, version){
  // TODO use proper images
  def specs = [
    "node": [
      "latest": [
            image: "openshift/jenkins-agent-nodejs-8-centos7",
            shell: '/bin/bash'
        ],
      "8.9": [
            image: "openshift/jenkins-agent-nodejs-8-centos7",
            shell: '/bin/bash'
      ],
      "4.6": [
            image: "openshift/jenkins-slave-nodejs-centos7",
            shell: '/bin/bash'
      ],
    ],
    "oc": [
      "latest": [
            image: "openshift/jenkins-agent-nodejs-8-centos7",
            shell: '/bin/bash'
      ],
      "3.11": [
            image: "openshift/jenkins-agent-nodejs-8-centos7",
            shell: '/bin/bash'
      ],
    ],
  ]

  // TODO: validate image in specs
  return specs[image][version]
}
