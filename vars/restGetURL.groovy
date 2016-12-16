#!/usr/bin/groovy
import groovy.json.JsonSlurperClassic

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()
  retry(3){
    HttpURLConnection connection = config.url.openConnection()
    if(config.authString.length() > 0)
    {
      connection.setRequestProperty("Authorization", "Bearer ${config.authString}")
    }
    connection.setRequestMethod("GET")
    connection.setDoInput(true)
    JsonSlurperClassic pr = null
    try {
      connection.connect()
      pr = new JsonSlurperClassic().parse(new InputStreamReader(connection.getInputStream(),"UTF-8"))
    } finally {
      connection.disconnect()
    }
    return pr
  }
}
