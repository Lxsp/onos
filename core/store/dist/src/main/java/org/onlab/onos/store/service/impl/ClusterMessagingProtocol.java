package org.onlab.onos.store.service.impl;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Vector;

import net.kuujo.copycat.cluster.TcpClusterConfig;
import net.kuujo.copycat.cluster.TcpMember;
import net.kuujo.copycat.event.LeaderElectEvent;
import net.kuujo.copycat.internal.log.ConfigurationEntry;
import net.kuujo.copycat.internal.log.CopycatEntry;
import net.kuujo.copycat.internal.log.OperationEntry;
import net.kuujo.copycat.internal.log.SnapshotEntry;
import net.kuujo.copycat.protocol.PingRequest;
import net.kuujo.copycat.protocol.PingResponse;
import net.kuujo.copycat.protocol.PollRequest;
import net.kuujo.copycat.protocol.PollResponse;
import net.kuujo.copycat.protocol.Response.Status;
import net.kuujo.copycat.protocol.SubmitRequest;
import net.kuujo.copycat.protocol.SubmitResponse;
import net.kuujo.copycat.protocol.SyncRequest;
import net.kuujo.copycat.protocol.SyncResponse;
import net.kuujo.copycat.spi.protocol.Protocol;
import net.kuujo.copycat.spi.protocol.ProtocolClient;
import net.kuujo.copycat.spi.protocol.ProtocolServer;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.onos.cluster.ClusterService;
import org.onlab.onos.store.cluster.messaging.ClusterCommunicationService;
import org.onlab.onos.store.cluster.messaging.MessageSubject;
import org.onlab.onos.store.serializers.KryoNamespaces;
import org.onlab.onos.store.serializers.KryoSerializer;
import org.onlab.onos.store.serializers.StoreSerializer;
import org.onlab.onos.store.service.impl.DatabaseStateMachine.State;
import org.onlab.onos.store.service.impl.DatabaseStateMachine.TableMetadata;
import org.onlab.util.KryoNamespace;
import org.slf4j.Logger;

/**
 * ONOS Cluster messaging based Copycat protocol.
 */
@Component(immediate = true)
@Service
public class ClusterMessagingProtocol
    implements DatabaseProtocolService, Protocol<TcpMember> {

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterCommunicationService clusterCommunicator;

    public static final MessageSubject COPYCAT_PING =
            new MessageSubject("copycat-raft-consensus-ping");
    public static final MessageSubject COPYCAT_SYNC =
            new MessageSubject("copycat-raft-consensus-sync");
    public static final MessageSubject COPYCAT_POLL =
            new MessageSubject("copycat-raft-consensus-poll");
    public static final MessageSubject COPYCAT_SUBMIT =
            new MessageSubject("copycat-raft-consensus-submit");

    static final int AFTER_COPYCAT = KryoNamespaces.BEGIN_USER_CUSTOM_ID + 50;

    static final KryoNamespace COPYCAT = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .nextId(KryoNamespaces.BEGIN_USER_CUSTOM_ID)
            .register(PingRequest.class)
            .register(PingResponse.class)
            .register(PollRequest.class)
            .register(PollResponse.class)
            .register(SyncRequest.class)
            .register(SyncResponse.class)
            .register(SubmitRequest.class)
            .register(SubmitResponse.class)
            .register(Status.class)
            .register(ConfigurationEntry.class)
            .register(SnapshotEntry.class)
            .register(CopycatEntry.class)
            .register(OperationEntry.class)
            .register(TcpClusterConfig.class)
            .register(TcpMember.class)
            .register(LeaderElectEvent.class)
            .register(Vector.class)
            .build();

    // serializer used for CopyCat Protocol
    public static final StoreSerializer DB_SERIALIZER = new KryoSerializer() {
        @Override
        protected void setupKryoPool() {
            serializerPool = KryoNamespace.newBuilder()
                    .register(COPYCAT)
                    .nextId(AFTER_COPYCAT)
                     // for snapshot
                    .register(State.class)
                    .register(TableMetadata.class)
                    // TODO: Move this out ?
                    .register(TableModificationEvent.class)
                    .register(TableModificationEvent.Type.class)
                    .build();
        }
    };

    @Activate
    public void activate() {
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        log.info("Stopped");
    }

    @Override
    public ProtocolServer createServer(TcpMember member) {
        return new ClusterMessagingProtocolServer(clusterCommunicator);
    }

    @Override
    public ProtocolClient createClient(TcpMember member) {
        return new ClusterMessagingProtocolClient(clusterService,
                                                  clusterCommunicator,
                                                  clusterService.getLocalNode(),
                                                  member);
    }
}
