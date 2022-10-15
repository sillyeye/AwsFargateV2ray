package com.myorg;

import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.NetworkLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.NetworkLoadBalancedTaskImageOptions;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

public class AwsSimpleFargateV2rayStack extends Stack {
    public AwsSimpleFargateV2rayStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AwsSimpleFargateV2rayStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = Vpc.Builder.create(this, "MyVpc")
                .maxAzs(2)  // Default is all AZs in region
                .build();

        Cluster cluster = Cluster.Builder.create(this, "MyCluster")
                .vpc(vpc)
                .build();

        int port = 8001;

        // Create a load-balanced Fargate service and make it public
        //ApplicationLoadBalancedFargateService service = ApplicationLoadBalancedFargateService.Builder.create(this, "MyFargateService")
        NetworkLoadBalancedFargateService fargateService = NetworkLoadBalancedFargateService.Builder.create(this, "V2rayFargateService")
                .cluster(cluster)           // Required
                .cpu(512)                   // Default is 256
                .desiredCount(2)            // Default is 1
                .taskImageOptions(
                        NetworkLoadBalancedTaskImageOptions.builder()
                                .image(ContainerImage.fromRegistry("v2ray/official"))
                                .containerPort(port)
                                .build())
                .memoryLimitMiB(1024)       // Default is 512
                .publicLoadBalancer(true)   // Default is true
                .listenerPort(port)
                .build();

        fargateService.getService().getConnections()
                .getSecurityGroups()
                .get(0)
                .addIngressRule(Peer.ipv4(vpc.getVpcCidrBlock()), Port.tcp(port), "allow http inbound from vpc");
    }
}
