def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  git push $GIT_REPOSITORY_URL $GIT_BRANCH

  curl -X POST -u $GIT_USER_NAME:$GIT_PASSWORD -k -d '{"title": "string replace for '$FILE_PATTERN' update from '$FROM' to '$TO'","head": "'$GIT_REPOSITORY_NAME':'$GIT_BRANCH'","base": "master"}' https://api.github.com/repos/$GIT_REPOSITORY_NAME/$GIT_PROJECT_NAME/pulls
}
