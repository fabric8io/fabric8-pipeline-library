import groovy.json.JsonSlurper

stage 'test'
node {
  waitUntil {
    test() == 'John Doe'
  }
}

def test() {
  //def object = new JsonSlurper().parseText('{ "name": "John Doe" }')
  def object = new JsonSlurper().parseText('{ "name": "John Doe" }')
  return object.name
}
