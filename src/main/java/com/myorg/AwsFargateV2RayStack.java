package com.myorg;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.util.List;

public class AwsFargateV2RayStack extends Stack {
    public AwsFargateV2RayStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AwsFargateV2RayStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = Vpc.Builder.create(this, "MyVpc")
                .maxAzs(2)  // Default is all AZs in region
                .build();

        Cluster cluster = Cluster.Builder.create(this, "MyCluster")
                .vpc(vpc)
                .build();

        final int containerPort = 8001, loadBalancePort = containerPort;

        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this, "FargateTaskDefinition")
                .cpu(512)
                .memoryLimitMiB(1024)
                .build();

        ContainerDefinition containerDefinition = taskDefinition.addContainer("V2rayContainer", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("v2ray/official"))
                .build());

        containerDefinition.addPortMappings(PortMapping.builder()
                .containerPort(containerPort)
                .hostPort(containerPort)
                .protocol(Protocol.TCP)
                .build());

        NetworkLoadBalancer loadBalancer = NetworkLoadBalancer.Builder.create(this, "LoadBalancer")
                .vpc(vpc)
                .internetFacing(true)
                .build();

        NetworkListener listener = loadBalancer.addListener("LBListener", BaseNetworkListenerProps.builder()
                .port(loadBalancePort)
                .build());

        FargateService fargateService = FargateService.Builder.create(this, "V2rayFargateService")
                .cluster(cluster)
                .taskDefinition(taskDefinition)
                .desiredCount(2)
                .build();

        listener.addTargets("Target", AddNetworkTargetsProps.builder()
                .port(containerPort)
                .targets(List.of(fargateService))
                .protocol(software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.TCP)
                .build());

        fargateService.getConnections()
                .getSecurityGroups()
                .get(0)
                .addIngressRule(Peer.ipv4(vpc.getVpcCidrBlock()), Port.tcp(containerPort), "allow http inbound from vpc");

        CfnOutput.Builder.create(this, "Load Balance domain name")
                .value(loadBalancer.getLoadBalancerDnsName())
                .build();
    }
}
