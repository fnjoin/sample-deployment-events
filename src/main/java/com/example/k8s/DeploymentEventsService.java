package com.example.k8s;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Service
public class DeploymentEventsService {

    private final SharedIndexInformer<V1Deployment> informer;

    public DeploymentEventsService(
            ApiClient client,
            SharedInformerFactory informerFactory,
            @Value("${namespace}") String namespace) {

        informer = informerFactory.sharedIndexInformerFor(
                params -> new AppsV1Api(client).listNamespacedDeploymentCall(
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

        informer.addEventHandler(new DeploymentEventsHandler());
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
