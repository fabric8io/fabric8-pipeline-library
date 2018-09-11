import io.openshift.Globals

def call(conf = [:]) {
  Globals.config << conf // merge
}

// usage: config.runtime()
def runtime() {
  return Globals.config.runtime
}

def version() {
  return Globals.config.version
}

def values() {
  return Globals.config
}
