#!/usr/bin/groovy
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def rc = """
    {
      "apiVersion" : "v1",
      "kind" : "Template",
      "labels" : { },
      "metadata" : {
        "annotations" : {
          "description" : "${config.label} example",
          "fabric8.${env.JOB_NAME}/iconUrl" : "${config.icon}"
        },
        "labels" : { },
        "name" : "${env.JOB_NAME}"
      },
      "objects" : [{
        "kind": "Service",
        "apiVersion": "v1",
        "metadata": {
            "name": "${env.JOB_NAME}",
            "creationTimestamp": null,
            "labels": {
                "component": "${env.JOB_NAME}",
                "container": "${config.label}",
                "group": "quickstarts",
                "project": "${env.JOB_NAME}",
                "provider": "fabric8",
                "expose": "true",
                "version": "${config.version}"
            },
            "annotations": {
                "fabric8.${env.JOB_NAME}/iconUrl" : "${config.icon}",
                "prometheus.io/port": "${config.port}",
                "prometheus.io/scheme": "http",
                "prometheus.io/scrape": "true"
            }
        },
        "spec": {
            "ports": [
                {
                    "protocol": "TCP",
                    "port": 80,
                    "targetPort": ${config.port}
                }
            ],
            "selector": {
                "component": "${env.JOB_NAME}",
                "container": "${config.label}",
                "group": "quickstarts",
                "project": "${env.JOB_NAME}",
                "provider": "fabric8",
                "version": "${config.version}"
            },
            "type": "LoadBalancer",
            "sessionAffinity": "None"
        }
    },
    {
        "kind": "ReplicationController",
        "apiVersion": "v1",
        "metadata": {
            "name": "${env.JOB_NAME}",
            "generation": 1,
            "creationTimestamp": null,
            "labels": {
                "component": "${env.JOB_NAME}",
                "container": "${config.label}",
                "group": "quickstarts",
                "project": "${env.JOB_NAME}",
                "provider": "fabric8",
                "expose": "true",
                "version": "${config.version}"
            },
            "annotations": {
                "fabric8.${env.JOB_NAME}/iconUrl" : "${config.icon}"
            }
        },
        "spec": {
            "replicas": 1,
            "selector": {
                "component": "${env.JOB_NAME}",
                "container": "${config.label}",
                "group": "quickstarts",
                "project": "${env.JOB_NAME}",
                "provider": "fabric8",
                "version": "${config.version}"
            },
            "template": {
                "metadata": {
                    "creationTimestamp": null,
                    "labels": {
                        "component": "${env.JOB_NAME}",
                        "container": "${config.label}",
                        "group": "quickstarts",
                        "project": "${env.JOB_NAME}",
                        "provider": "fabric8",
                        "version": "${config.version}"
                    }
                },
                "spec": {
                    "containers": [
                        {
                            "name": "${env.JOB_NAME}",
                            "image": "${env.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST}:${env.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT}/${env.KUBERNETES_NAMESPACE}/${env.JOB_NAME}:${config.version}",
                            "ports": [
                                {
                                    "name": "web",
                                    "containerPort": ${config.port},
                                    "protocol": "TCP"
                                }
                            ],
                            "env": [
                                {
                                    "name": "KUBERNETES_NAMESPACE",
                                    "valueFrom": {
                                        "fieldRef": {
                                            "apiVersion": "v1",
                                            "fieldPath": "metadata.namespace"
                                        }
                                    }
                                }
                            ],
                            "resources": {},
                            "terminationMessagePath": "/dev/termination-log",
                            "imagePullPolicy": "IfNotPresent",
                            "securityContext": {}
                        }
                    ],
                    "restartPolicy": "Always",
                    "terminationGracePeriodSeconds": 30,
                    "dnsPolicy": "ClusterFirst",
                    "securityContext": {}
                }
            }
        },
        "status": {
            "replicas": 0
        }
    }]}
    """

    echo 'using Kubernetes resources:\n' + rc
    return rc

  }
