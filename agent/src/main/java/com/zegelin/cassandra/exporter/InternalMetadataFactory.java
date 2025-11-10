package com.zegelin.cassandra.exporter;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.statements.schema.IndexTarget;
import org.apache.cassandra.locator.IEndpointSnitch;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.schema.Schema;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Optional;
import java.util.Set;

public class InternalMetadataFactory extends MetadataFactory {

    /**
     * Reflective accessor for Schema.getKeyspaces().
     * In 4.0.x it returns Set&lt;String&gt;, in 4.1.x it returns Sets.SetView (which extends Set).
     * The JVM method descriptor differs, so direct compiled calls fail across versions.
     * Reflection bypasses the descriptor check.
     */
    private static final Method GET_KEYSPACES;

    static {
        try {
            GET_KEYSPACES = Schema.class.getMethod("getKeyspaces");
        } catch (final NoSuchMethodException e) {
            throw new ExceptionInInitializerError("Schema.getKeyspaces() not found");
        }
    }

    private static Optional<org.apache.cassandra.schema.TableMetadata> getTableMetaData(final String keyspaceName, final String tableName) {
        return Optional.ofNullable(Schema.instance.getTableMetadata(keyspaceName, tableName));
    }

    private static Optional<org.apache.cassandra.schema.TableMetadataRef> getIndexMetadata(final String keyspaceName, final String indexName) {
        return Optional.ofNullable(Schema.instance.getIndexTableMetadataRef(keyspaceName, indexName));
    }

    @Override
    public Optional<IndexMetadata> indexMetadata(final String keyspaceName, final String tableName, final String indexName) {
        return getIndexMetadata(keyspaceName, indexName)
                .flatMap(tableMetadata -> tableMetadata.get().indexes.get(indexName))
                .map(indexMetadata -> {
                    final IndexMetadata.IndexType indexType = IndexMetadata.IndexType.valueOf(indexMetadata.kind.name());
                    final Optional<String> className = Optional.ofNullable(indexMetadata.options.get(IndexTarget.CUSTOM_INDEX_OPTION_NAME));
                    return new IndexMetadata() {
                        @Override
                        public IndexType indexType() {
                            return indexType;
                        }

                        @Override
                        public Optional<String> customClassName() {
                            return className;
                        }
                    };
                });
    }

    @Override
    public Optional<TableMetadata> tableOrViewMetadata(final String keyspaceName, final String tableOrViewName) {
        return getTableMetaData(keyspaceName, tableOrViewName)
                .map(m -> new TableMetadata() {
                    @Override
                    public String compactionStrategyClassName() {
                        return m.params.compaction.klass().getCanonicalName();
                    }

                    @Override
                    public boolean isView() {
                        return m.isView();
                    }
                });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> keyspaces() {
        try {
            return (Set<String>) GET_KEYSPACES.invoke(Schema.instance);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke Schema.getKeyspaces()", e);
        }
    }

    @Override
    public Optional<EndpointMetadata> endpointMetadata(final InetAddress endpoint) {
        final IEndpointSnitch endpointSnitch = DatabaseDescriptor.getEndpointSnitch();

        return Optional.of(new EndpointMetadata() {
            @Override
            public String dataCenter() {
                return endpointSnitch.getDatacenter(InetAddressAndPort.getByAddress(endpoint));
            }

            @Override
            public String rack() {
                return endpointSnitch.getRack(InetAddressAndPort.getByAddress(endpoint));
            }
        });
    }

    @Override
    public String clusterName() {
        return DatabaseDescriptor.getClusterName();
    }

    @Override
    public InetAddress localBroadcastAddress() {
        return InetAddressAndPortCompat.getAddress(FBUtilities.getBroadcastAddressAndPort());
    }
}
