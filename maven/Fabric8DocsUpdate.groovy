stage 'update the docs and website'

def isRelease = ""
try {
  isRelease = IS_RELEASE
} catch (Throwable e) {
  isRelease = "${env.IS_RELEASE ?: 'true'}"
}

updateDocs{
  isRelease = isRelease
}
