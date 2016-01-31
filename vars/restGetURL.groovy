#!/usr/bin/groovy
import groovy.json.JsonSlurper;

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  retry(3){
    def HttpURLConnection connection = config.url.openConnection()
    if(config.authString.length() > 0)
    {
      def conn = config.url.openConnection()
      connection.setRequestProperty("Authorization", "Bearer ${config.authString}")
    }
    connection.setRequestMethod("GET")
    connection.setDoInput(true)
    connection.connect()
    def pr = new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(),"UTF-8"))
    connection.disconnect()

    return pr
  }

}
