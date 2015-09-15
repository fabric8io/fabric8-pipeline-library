stage 'wait'
node {
   ws ('wait') {
    waitUntil {
      mavenCentralVersion() == '2.2.29'
    }
  }
}

def mavenCentralVersion() {
  def modelMetaData = new XmlSlurper().parse("http://central.maven.org/maven2/io/fabric8/fabric8-maven-plugin/maven-metadata.xml")
  def version = modelMetaData.versioning.release.text()
  return version
}
