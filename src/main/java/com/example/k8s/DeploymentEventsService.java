package com.example.k8s;

import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Optional;

@Slf4j
@Service
public class DeploymentEventsService {

    private final SharedIndexInformer<V1Deployment> informer;

    public DeploymentEventsService(
            ApiClient client,
            SharedInformerFactory informerFactory,
            @Value("${namespace}") String namespace) {

        informer = informerFactory.sharedIndexInformerFor(params -> new AppsV1Api(client).listNamespacedDeploymentCall(
                        namespace,
                        null,
                        null,
                        null,
                        null,
                        "team",
                        null,
                        params.resourceVersion,
                        null,
                        params.timeoutSeconds,
                        params.watch,
                        null),
                V1Deployment.class,
                V1DeploymentList.class);

        informer.addEventHandler(new ResourceEventHandler<V1Deployment>() {
            @Override
            public void onAdd(V1Deployment obj) {
                /* don't need to pay attention to this event for our use case */
            }

            @Override
            public void onDelete(V1Deployment obj, boolean deletedFinalStateUnknown) {
                /* don't need to pay attention to this event for our use case */
            }

            @Override
            public void onUpdate(V1Deployment oldObj, V1Deployment newObj) {

                debug(oldObj, "updated-old");
                debug(newObj, "updated-new");

                logDeploymentStatusIfInteresting(oldObj, newObj);
            }
        });
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

    private boolean hasCondition(V1Deployment deployment, String reason, String progressing) {
        return Optional.ofNullable(deployment.getStatus())
                .filter(status -> status.getConditions() != null)
                .filter(status -> status.getConditions().stream()
                        .filter(c -> c.getType().equals("Progressing") && c.getReason().equals(reason) && c.getStatus().equals(progressing))
                        .findFirst()
                        .isPresent())
                .isPresent();
    }

    private void logDeploymentStatusIfInteresting(V1Deployment oldDeployment, V1Deployment newDeployment) {
        if (!hasCondition(oldDeployment, "NewReplicaSetCreated", "True") && hasCondition(newDeployment, "NewReplicaSetCreated", "True")) {
            logDeploymentStatus(newDeployment, "Started");
        } else if (hasCondition(oldDeployment, "ReplicaSetUpdated", "True")) {
            if (hasCondition(newDeployment, "NewReplicaSetAvailable", "True")) {
                logDeploymentStatus(newDeployment, "Finished/Success");
            } else if (hasCondition(newDeployment, "ProgressDeadlineExceeded", "False")) {
                logDeploymentStatus(newDeployment, "Finished/Failed");
            }
        }
    }

    private void logDeploymentStatus(V1Deployment newDeployment, String s) {
        log.info("***** Deployment: App={}, Team={}, Status={} *****",
                newDeployment.getMetadata().getName(),
                newDeployment.getMetadata().getLabels().get("team"),
                s);
    }

    @PostConstruct
    public void init() {
        informer.run();
    }

    @PreDestroy
    public void destroy() {
        informer.stop();
    }
}
