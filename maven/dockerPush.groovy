node {

    // stage 'quickstarts'
    //   ws ('ipaas-quickstarts'){
    //     withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
    //
    //
    //       sh "mvn docker:push -P release,quickstarts"
    //     }

    stage 'ipaas'
    ws ('fabric8-ipaas'){
      withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {


        sh "mvn docker:push -P release,quickstarts"
      }


    // stage 'devops'
    // ws ('fabric8-devops'){
    //   withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
    //
    //
    //     sh "mvn docker:push -P release,quickstarts"
    //   }
}
}
