#!/usr/bin/groovy
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurperClassic

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()
  retry(3){
    return getResult(config.url, config.authString)
  }
}

@NonCPS
def getResult(url, authString){
  HttpURLConnection connection = url.openConnection()
  if(authString != null && authString.length() > 0)
  {
    connection.setRequestProperty("Authorization", "Bearer ${authString}")
  }
  connection.setRequestMethod("GET")
  connection.setDoInput(true)
  def rs = null
  try {
    connection.connect()
    rs = new JsonSlurperClassic().parse(new InputStreamReader(connection.getInputStream(),"UTF-8"))
  } finally {
    connection.disconnect()
  }
  return rs
}