#!/usr/bin/groovy

def call(Map args = [:], body = null){
    def spec = specForImage(args.image, args.version?: 'latest')
    def checkoutScm = args.checkout_scm ?: true
    pod(name: args.image, image: spec.image, shell: spec.shell) {
      if (checkoutScm) {
        checkout scm
      }
      body()
    }
}

def specForImage(image, version){
  // TODO use proper images
  def specs = [
    "node": [
      "latest": [
            image: "piyushgarg/test${image}:latest",
            shell: '/bin/bash'
        ],
      "8.9": [
            image: "piyushgarg/test${image}:${version}",
            shell: '/bin/bash'
      ],
    ],
    "oc": [
      "latest": [
            image: "piyushgarg/testnode:latest",
            shell: '/bin/bash'
      ],
    ],
  ]

  // TODO: validate image in specs
  return specs[image][version]
}
