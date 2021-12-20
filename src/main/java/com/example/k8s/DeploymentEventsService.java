package com.example.k8s;

import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import lombok.Builder;
import lombok.Getter;
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

        log.info("Creating asynchronous team-app service, Namespace={}", namespace);
        AppsV1Api appsV1Api = new AppsV1Api(client);

        informer = informerFactory.sharedIndexInformerFor(params -> appsV1Api.listNamespacedDeploymentCall(
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
            public void onUpdate(V1Deployment oldObj, V1Deployment newObj) {
                handleDeploymentUpdated(oldObj, newObj);
            }

            @Override
            public void onDelete(V1Deployment obj, boolean deletedFinalStateUnknown) {
                /* don't need to pay attention to this event for our use case */
            }
        });
    }

    private void handleDeploymentUpdated(V1Deployment oDeployment, V1Deployment nDeployment) {

        debug(oDeployment, "updated-old");
        debug(nDeployment, "updated-new");

        getDeploymentStatusIfDone(oDeployment, nDeployment)
                .ifPresent(status -> log.info("Deployment: App={}, Team={}, Status={}",
                        status.getApp(),
                        status.getTeam(),
                        status.getStatus()));
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

    private Optional<DeploymentStatus> getDeploymentStatusIfDone(V1Deployment oldDeployment, V1Deployment newDeployment) {

        if (!hasCondition(oldDeployment, "NewReplicaSetCreated", "True") && hasCondition(newDeployment, "NewReplicaSetCreated", "True")) {
            return Optional.of(DeploymentStatus.builder()
                    .app(newDeployment.getMetadata().getName())
                    .team(newDeployment.getMetadata().getLabels().get("team"))
                    .status("Started")
                    .build());
        }

        if (hasCondition(oldDeployment, "ReplicaSetUpdated", "True")) {
            if (hasCondition(newDeployment, "NewReplicaSetAvailable", "True")) {
                return Optional.of(DeploymentStatus.builder()
                        .app(newDeployment.getMetadata().getName())
                        .team(newDeployment.getMetadata().getLabels().get("team"))
                        .status("Finished/Success")
                        .build());
            }
            if (hasCondition(newDeployment, "ProgressDeadlineExceeded", "False")) {
                return Optional.of(DeploymentStatus.builder()
                        .app(newDeployment.getMetadata().getName())
                        .team(newDeployment.getMetadata().getLabels().get("team"))
                        .status("Finished/Failed")
                        .build());
            }
        }

        return Optional.empty();
    }

    @Builder
    @Getter
    public static class DeploymentStatus {
        String app;
        String team;
        String status;
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
