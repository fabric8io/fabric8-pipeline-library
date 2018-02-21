#!/usr/bin/groovy
import com.cloudbees.groovy.cps.NonCPS

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  echo "deleting ${config.project} branch: ${config.branch}"
  deleteBranch(config.project, config.branch, config.authString)

}

@NonCPS
def deleteBranch(String project, String branch, String authString){
  apiUrl = new URL("https://api.github.com/repos/${project}/git/refs/heads/${branch}")

  retry(3){
    HttpURLConnection connection = apiUrl.openConnection()
    if(authString.length() > 0)
    {
      connection.setRequestProperty("Authorization", "Bearer ${authString}")
    }
    connection.setRequestMethod("DELETE")

    try {
      connection.connect()

      if (!isSuccessfulCode(connection.getResponseCode())) {
        error ("DELETE was not successful: " + connection.getResponseCode() + " "+ connection.getResponseMessage())
      }

    } finally {
      connection.disconnect()
    }
  }
}

def isSuccessfulCode(int responseCode) {
  return responseCode >= 200 && responseCode < 300 // 2xx => successful
}
