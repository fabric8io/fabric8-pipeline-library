#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def flow = new io.fabric8.Fabric8Commands()

    def expose = config.exposeApp ?: 'true'
    def requestCPU = config.resourceRequestCPU ?: '0'
    def requestMemory = config.resourceRequestMemory ?: '0'
    def limitCPU = config.resourceLimitMemory ?: '0'
    def limitMemory = config.resourceLimitMemory ?: '0'
    def yaml

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
      project: ${env.JOB_NAME}
      expose: '${expose}'
      version: ${config.version}
      group: quickstart
    name: ${env.JOB_NAME}
  spec:
    ports:
    - port: 80
      protocol: TCP
      targetPort: ${config.port}
    selector:
      project: ${env.JOB_NAME}
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
      project: ${env.JOB_NAME}
      version: ${config.version}
      group: quickstart
    name: ${env.JOB_NAME}
  spec:
    replicas: 1
    selector:
      matchLabels:
        provider: fabric8
        project: ${env.JOB_NAME}
        group: quickstart
    template:
      metadata:
        labels:
          provider: fabric8
          project: ${env.JOB_NAME}
          version: ${config.version}
          group: quickstart
      spec:
        containers:
        - env:
          - name: KUBERNETES_NAMESPACE
            valueFrom:
              fieldRef:
                fieldPath: metadata.namespace
          image: ${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${env.KUBERNETES_NAMESPACE}/${env.JOB_NAME}:${config.version}
          imagePullPolicy: IfNotPresent
          name: ${env.JOB_NAME}
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
      project: ${env.JOB_NAME}
      version: ${config.version}
      group: quickstart
    name: ${env.JOB_NAME}
  spec:
    replicas: 1
    selector:
      provider: fabric8
      project: ${env.JOB_NAME}
      group: quickstart
    template:
      metadata:
        labels:
          provider: fabric8
          project: ${env.JOB_NAME}
          version: ${config.version}
          group: quickstart
      spec:
        containers:
        - env:
          - name: KUBERNETES_NAMESPACE
            valueFrom:
              fieldRef:
                fieldPath: metadata.namespace
          image: ${env.DOCKER_REGISTRY_SERVICE_HOST}:${env.DOCKER_REGISTRY_SERVICE_PORT}/${env.KUBERNETES_NAMESPACE}/${env.JOB_NAME}:${config.version}
          imagePullPolicy: IfNotPresent
          name: ${env.JOB_NAME}
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
        terminationGracePeriodSeconds: 2
    triggers:
    - type: ConfigChange
"""


  if (flow.isOpenShift()){
    yaml = list + service + deploymentConfig
  } else {
    yaml = list + service + deployment
  }

  echo 'using resources:\n' + yaml
  return yaml

  }
