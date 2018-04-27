#!/usr/bin/groovy
import io.fabric8.Fabric8Commands
import io.fabric8.Utils

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new Fabric8Commands()
    def utils = new Utils()

    def resourceName = utils.getResourceName()
    def expose = config.exposeApp ?: 'true'
    def requestCPU = config.resourceRequestCPU ?: '0'
    def requestMemory = config.resourceRequestMemory ?: '0'
    def limitCPU = config.resourceLimitMemory ?: '0'
    def limitMemory = config.resourceLimitMemory ?: '0'
    def yaml

    def isSha = ''
    if (flow.isOpenShift()) {
        isSha = utils.getImageStreamSha(resourceName)
    }

    def fabric8Registry = ''
    if (env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST) {
        fabric8Registry = env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST + ':' + env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT + '/'
    }

    def sha
    def list = """
---
apiVersion: v1
kind: List
items:
"""

    def service = """
- apiVersion: v1
  kind: Service
  metadata:
    annotations:
      fabric8.io/iconUrl: ${config.icon}
    labels:
      provider: fabric8
      project: ${resourceName}
      expose: '${expose}'
      version: ${config.version}
      group: quickstart
    name: ${resourceName}
  spec:
    ports:
    - port: 80
      protocol: TCP
      targetPort: ${config.port}
    selector:
      project: ${resourceName}
      provider: fabric8
      group: quickstart
"""

    def deployment = """
- apiVersion: extensions/v1beta1
  kind: Deployment
  metadata:
    annotations:
      fabric8.io/iconUrl: ${config.icon}
    labels:
      provider: fabric8
      project: ${resourceName}
      version: ${config.version}
      group: quickstart
    name: ${resourceName}
  spec:
    replicas: 1
    selector:
      matchLabels:
        provider: fabric8
        project: ${resourceName}
        group: quickstart
    template:
      metadata:
        labels:
          provider: fabric8
          project: ${resourceName}
          version: ${config.version}
          group: quickstart
      spec:
        containers:
        - env:
          - name: KUBERNETES_NAMESPACE
            valueFrom:
              fieldRef:
                fieldPath: metadata.namespace
          image: ${fabric8Registry}${env.KUBERNETES_NAMESPACE}/${env.JOB_NAME}:${config.version}
          imagePullPolicy: IfNotPresent
          name: ${resourceName}
          ports:
          - containerPort: ${config.port}
            name: http
          resources:
            limits:
              cpu: ${requestCPU}
              memory: ${requestMemory}
            requests:
              cpu: ${limitCPU}
              memory: ${limitMemory}
          readinessProbe:
            httpGet:
              path: "/"
              port: ${config.port}
            initialDelaySeconds: 1
            timeoutSeconds: 5
            failureThreshold: 5
          livenessProbe:
            httpGet:
              path: "/"
              port: ${config.port}
            initialDelaySeconds: 180
            timeoutSeconds: 5
            failureThreshold: 5
        terminationGracePeriodSeconds: 2
"""

    def deploymentConfig = """
- apiVersion: v1
  kind: DeploymentConfig
  metadata:
    annotations:
      fabric8.io/iconUrl: ${config.icon}
    labels:
      provider: fabric8
      project: ${resourceName}
      version: ${config.version}
      group: quickstart
    name: ${resourceName}
  spec:
    replicas: 1
    selector:
      provider: fabric8
      project: ${resourceName}
      group: quickstart
    template:
      metadata:
        labels:
          provider: fabric8
          project: ${resourceName}
          version: ${config.version}
          group: quickstart
      spec:
        containers:
        - env:
          - name: KUBERNETES_NAMESPACE
            valueFrom:
              fieldRef:
                fieldPath: metadata.namespace
          image: ${resourceName}:${config.version}
          imagePullPolicy: IfNotPresent
          name: ${resourceName}
          ports:
          - containerPort: ${config.port}
            name: http
          resources:
            limits:
              cpu: ${requestCPU}
              memory: ${requestMemory}
            requests:
              cpu: ${limitCPU}
              memory: ${limitMemory}
          readinessProbe:
            httpGet:
              path: "/"
              port: ${config.port}
            initialDelaySeconds: 1
            timeoutSeconds: 5
            failureThreshold: 5
          livenessProbe:
            httpGet:
              path: "/"
              port: ${config.port}
            initialDelaySeconds: 180
            timeoutSeconds: 5
            failureThreshold: 5
        terminationGracePeriodSeconds: 2
    triggers:
    - type: ConfigChange
    - imageChangeParams:
        automatic: true
        containerNames:
        - ${resourceName}
        from:
          kind: ImageStreamTag
          name: ${resourceName}:${config.version}
      type: ImageChange
"""

    def is = """
- apiVersion: v1
  kind: ImageStream
  metadata:
    name: ${resourceName}
  spec:
    tags:
    - from:
        kind: ImageStreamImage
        name: ${resourceName}@${isSha}
        namespace: ${utils.getNamespace()}
      name: ${config.version}
"""

    if (flow.isOpenShift()) {
        yaml = list + service + is + deploymentConfig
    } else {
        yaml = list + service + deployment
    }

    echo 'using resources:\n' + yaml
    return yaml

}
