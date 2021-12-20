# deployment-events

### Build Pre-requisites

- JDK 11
- `kubectl`

### Running Pre-requisites

- Access to a Kubernetes cluster
- A namespace with some running deployments (`dev` by default)
- The `team` label assigned to all deployments that you want this app to pay attention to

### Building/Running the app

```
./gradlew bootRun
```

If you want to use target a Kubernetes namespace other than dev, use the `NAMESPACE` env variable:

```
NAMESPACE=other-ns ./gradlew bootRun
```
