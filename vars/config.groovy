import io.openshift.Globals

def call(conf = [:]) {
  Globals.config << conf // merge
}

// usage: config.runtime()
def runtime() {
  return Globals.config.runtime
}

def values() {
  return Globals.config
}
