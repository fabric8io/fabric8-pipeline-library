#!/usr/bin/groovy

def call(Map args = [:], body = null) {
    def label = buildId(args.name)

    podTemplate(
      label: label,
      cloud: 'openshift',
      serviceAccount: 'jenkins',
      inheritFrom: 'base',
      containers: [
        slaveTemplate(args.name, args.image, args.shell),
        jnlpTemplate()
      ],
      volumes: volumes(),
    ) {
      node (label) {
        container(name: args.name, shell: args.shell) {
            body()
        }
      }
    }

}

def volumes() {
  [
    secretVolume(secretName: 'jenkins-release-gpg', mountPath: '/home/jenkins/.gnupg-ro'),
    secretVolume(secretName: 'jenkins-hub-api-token', mountPath: '/home/jenkins/.apitoken'),
    secretVolume(secretName: 'jenkins-ssh-config', mountPath: '/root/.ssh-ro'),
    secretVolume(secretName: 'jenkins-git-ssh', mountPath: '/root/.ssh-git-ro')
  ]
}

def slaveTemplate(name, image, shell) {
    containerTemplate(
        name: name,
        image: image,
        command: "$shell -c",
        args: 'cat',
        ttyEnabled: true,
        workingDir: '/home/jenkins/',
        resourceLimitMemory: '640Mi'
    )
}

def jnlpTemplate() {
    def jnlpImage = 'fabric8/jenkins-slave-base-centos7:vb0268ae'

    return containerTemplate(
        name: 'jnlp',
        image: "${jnlpImage}",
        args: '${computer.jnlpmac} ${computer.name}',
        workingDir: '/home/jenkins/',
        resourceLimitMemory: '256Mi'
    )
}
