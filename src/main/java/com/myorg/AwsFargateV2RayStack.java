package com.myorg;

import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.*;
import software.amazon.awscdk.services.elasticloadbalancing.LoadBalancerListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.util.ArrayList;
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

        int port = 8001;

        // Create a load-balanced Fargate service and make it public
        //ApplicationLoadBalancedFargateService service = ApplicationLoadBalancedFargateService.Builder.create(this, "MyFargateService")
        /*

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
         */

        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this, "FargateTaskDefinition")
                .cpu(512)
                .memoryLimitMiB(1024)
                .build();

        ContainerDefinition containerDefinition = taskDefinition.addContainer("V2rayContainer", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("v2ray/official"))
                .build());

        containerDefinition.addPortMappings(PortMapping.builder()
                .containerPort(port)
                .hostPort(port)
                .protocol(Protocol.TCP)
                .build());

        NetworkLoadBalancer loadBalancer = NetworkLoadBalancer.Builder.create(this, "LoadBalancer")
                .vpc(vpc)
                .internetFacing(true)
                .build();

        NetworkListener listener = loadBalancer.addListener("LBListener", BaseNetworkListenerProps.builder()
                .port(port)
                .build());

        FargateService fargateService = FargateService.Builder.create(this, "V2rayFargateService")
                .cluster(cluster)
                .taskDefinition(taskDefinition)
                .desiredCount(2)
                .build();

        IEcsLoadBalancerTarget target = fargateService.loadBalancerTarget(LoadBalancerTargetOptions.builder()
                .containerPort(port)
                .containerName("V2rayContainer")
                .build());

        listener.addTargets("Target", AddNetworkTargetsProps.builder()
                .port(port)
                .targets(List.of(target))
                .protocol(software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.TCP)
                .build());

        /*
        NetworkTargetGroup networkTargetGroup = NetworkTargetGroup.Builder.create(this, "NetworkTargetGroup")
                .port(port)
                .vpc(vpc)
                .protocol(software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.TCP)
                .targets(list).build();

        ArrayList<INetworkLoadBalancerTarget> list2 = new ArrayList<INetworkLoadBalancerTarget>();
        list2.add(networkTargetGroup);
        fargateService.registerLoadBalancerTargets(EcsTarget.builder()
                .containerName("V2rayContainer")
                .containerPort(port)
                .newTargetGroupId("ECS")
                .listener(ListenerConfig.networkListener(listener, AddNetworkTargetsProps.builder()
                        .port(port)
                        .protocol(software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.TCP)
                        .build()))
                .build());
         */

        fargateService.getConnections()
        //fargateService.getService().getConnections()
                .getSecurityGroups()
                .get(0)
                .addIngressRule(Peer.ipv4(vpc.getVpcCidrBlock()), Port.tcp(port), "allow http inbound from vpc");
    }
}
