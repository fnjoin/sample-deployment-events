# sample-deployment-events

A sample spring-boot app that uses *Kubernetes API for Java* to detect important deployment events. Specifically, if detects when a *Deployment* has started and when it finishes.

### Build Pre-requisites

- JDK 11

### Running Pre-requisites

- Access to a Kubernetes cluster via `kubectl`
- A namespace where you can schedule some deployments (`dev` by default)
- The `team` label assigned to all deployments that you want this app to pay attention to

### Building/Running the app

```
./gradlew bootRun
```

If you want to target a Kubernetes namespace other than `dev`, use the `NAMESPACE` environment variable:

```
NAMESPACE=other-ns ./gradlew bootRun
```
