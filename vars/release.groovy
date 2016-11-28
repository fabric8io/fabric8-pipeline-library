#!/usr/bin/groovy
def call(Map parameters = [:], body) {

    def label = "buildpod.${env.JOB_NAME}.${env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
    def jnlpImage = parameters.get('jnlpImage', 'fabric8/jnlp-client:latest')
    podTemplate(label: label, serviceAccount: 'jenkins', containers: [
            [name: 'jnlp', image: "${jnlpImage}", command:'/usr/local/bin/start.sh', args: '${computer.jnlpmac} ${computer.name}', ttyEnabled: false,
             envVars: [
                        [key: 'DOCKER_HOST', value: 'unix:/var/run/docker.sock'],
                        [key: 'SONATYPE_USERNAME', value: '${SONATYPE_USERNAME}'],
                        [key: 'SONATYPE_PASSWORD', value: '${SONATYPE_PASSWORD}'],
                        [key: 'GPG_PASSPHRASE', value: '${GPG_PASSPHRASE}'],
                        [key: 'NEXUS_USERNAME', value: '${NEXUS_USERNAME}'],
                        [key: 'NEXUS_PASSWORD', value: '${NEXUS_PASSWORD}'],
                      ]
            ]],
            volumes: [
                    [$class: 'SecretVolume', mountPath: '/root/.gnupg', secretName: 'jenkins-release-gpg']
            ]) {
        node(label) {
            body()
        }
    }
}
