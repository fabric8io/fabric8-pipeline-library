package io.fabric8
import io.fabric8.plugins.*

class Plugins implements Serializable {

    static def register() {
      new analytics().register()
    }
}

