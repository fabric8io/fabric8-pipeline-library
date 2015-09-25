stage 'update the docs and website'

def release = ""
try {
  release = IS_RELEASE
} catch (Throwable e) {
  release = "${env.IS_RELEASE ?: 'true'}"
}

updateDocs{
  isRelease = release
}
