import groovy.json.JsonSlurper;
stage 'one'

node ('swarm'){
  ws('test3'){
  authString = "${env.GITHUB_TOKEN}"

  echo "using token ${authString}"
  def apiUrl = new URL("https://api.github.com/repos/fabric8io/ipaas-quickstarts/pulls/797")


  def HttpURLConnection connection = apiUrl.openConnection()
  if(authString.length() > 0)
  {
    echo "opening connection"
    def conn = apiUrl.openConnection()

    connection.setRequestProperty("Authorization", "Bearer ${authString}")
  }
  connection.setRequestMethod("GET")
  connection.setDoInput(true)
  connection.connect()
  def result = new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(),"UTF-8"))
  connection.disconnect()


branchName = result.head.ref

echo "branch: ${branchName}"

  }
}
