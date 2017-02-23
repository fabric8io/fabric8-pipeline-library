#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()
    container(name: 'maven') {

        // update any versions that we want to override
        for ( v in config.pomVersionToUpdate ) {
            flow.searchAndReplaceMavenVersionProperty(v.key, v.value)
        }

        sh "mvn clean -e -U deploy"

        def s2iMode = flow.isOpenShiftS2I()
        echo "s2i mode: ${s2iMode}"
        def m = readMavenPom file: 'pom.xml'
        def version = m.version

        if (!s2iMode){
            if (flow.isSingleNode()){
                echo 'Running on a single node, skipping docker push as not needed'

                def groupId = m.groupId.split( '\\.' )
                def user = groupId[groupId.size()-1].trim()
                def artifactId = m.artifactId
                sh "docker tag ${user}/${artifactId}:${version} ${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${user}/${artifactId}:${version}"

            }else{
                retry(3){
                    sh "mvn fabric8:push -Ddocker.push.registry=${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}"
                }
            }
        }
        return version
    }
  }
