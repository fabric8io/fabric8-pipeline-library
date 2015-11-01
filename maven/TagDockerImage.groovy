String myproject = 'fabric8-devops'
String[] tagDockerImages = ['fabric8-console','hubot-irc','eclipse-orion','nexus','gerrit','fabric8-kiwiirc','brackets','jenkins-swarm-client','taiga-front','taiga-back','hubot-slack','lets-chat','jenkernetes']
String version = '2.2.63'

tagImages{
  project = myproject
  images = tagDockerImages
  tag = version
}
