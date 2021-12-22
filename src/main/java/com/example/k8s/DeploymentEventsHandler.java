package com.example.k8s;

import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class DeploymentEventsHandler implements ResourceEventHandler<V1Deployment> {

    public void onAdd(V1Deployment obj) {
        /* don't need to pay attention to this event for our use case */
    }

    public void onDelete(V1Deployment obj, boolean deletedFinalStateUnknown) {
        /* don't need to pay attention to this event for our use case */
    }

    public void onUpdate(V1Deployment oldObj, V1Deployment newObj) {

        debug(oldObj, "updated-old");
        debug(newObj, "updated-new");

        if (!hasCondition(oldObj, "ReplicaSetUpdated", "True") && hasCondition(newObj, "ReplicaSetUpdated", "True")) {
            logDeploymentStatus(newObj, "Started");
        } else if (hasCondition(oldObj, "ReplicaSetUpdated", "True")) {
            if (hasCondition(newObj, "NewReplicaSetAvailable", "True")) {
                logDeploymentStatus(newObj, "Finished/Success");
            } else if (hasCondition(newObj, "ProgressDeadlineExceeded", "False")) {
                logDeploymentStatus(newObj, "Finished/Failed");
            }
        }
    }

    private void debug(V1Deployment deployment, String event) {
        if (log.isDebugEnabled()) {
            if (deployment.getStatus() != null && deployment.getStatus().getConditions() != null) {
                deployment.getStatus().getConditions().stream()
                        .filter(c -> c.getType().equals("Progressing"))
                        .findFirst()
                        .ifPresent(c -> log.debug("Progressing: Event={}, Version={}, Reason={}, Status={}",
                                event,
                                deployment.getMetadata().getResourceVersion(),
                                c.getReason(),
                                c.getStatus()));
            } else {
                log.debug("Progressing: Event={}, NO-INFO-AVAILABLE", event);
            }
        }
    }

    private boolean hasCondition(V1Deployment deployment, String progressingReason, String progressingStatus) {
        return Optional.ofNullable(deployment.getStatus())
                .filter(status -> status.getConditions() != null)
                .filter(status -> hasReasonAndStatus(status, progressingReason, progressingStatus))
                .isPresent();
    }

    private boolean hasReasonAndStatus(V1DeploymentStatus status, String progressingReason, String progressingStatus) {
        return status.getConditions().stream()
                .filter(c -> c.getType().equals("Progressing") && c.getReason().equals(progressingReason) && c.getStatus().equals(progressingStatus))
                .findFirst()
                .isPresent();
    }

    private void logDeploymentStatus(V1Deployment deployment, String status) {
        log.info("***** Deployment: App={}, Team={}, Status={} *****",
                deployment.getMetadata().getName(),
                deployment.getMetadata().getLabels().get("team"),
                status);
    }
}
