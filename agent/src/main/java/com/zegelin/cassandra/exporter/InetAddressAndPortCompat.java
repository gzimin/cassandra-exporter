package com.zegelin.cassandra.exporter;

import org.apache.cassandra.locator.InetAddressAndPort;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;

/**
 * Compatibility helper for extracting {@link InetAddress} from {@link InetAddressAndPort}
 * across different Cassandra versions.
 * <p>
 * Cassandra 4.0.x: {@code address} is a public field, no getter method.
 * Cassandra 4.1.x: {@code address} is private, accessed via {@code getAddress()}.
 * <p>
 * This class uses reflection resolved once at class load time, so it works
 * at runtime regardless of which Cassandra version is on the classpath.
 */
public final class InetAddressAndPortCompat {

    @FunctionalInterface
    private interface AddressExtractor {
        InetAddress extract(InetAddressAndPort endpoint) throws ReflectiveOperationException;
    }

    private static final AddressExtractor EXTRACTOR;

    static {
        AddressExtractor resolved;
        try {
            final Field field = InetAddressAndPort.class.getField("address");
            resolved = endpoint -> (InetAddress) field.get(endpoint);
        } catch (final NoSuchFieldException e) {
            try {
                final Method method = InetAddressAndPort.class.getMethod("getAddress");
                resolved = endpoint -> (InetAddress) method.invoke(endpoint);
            } catch (final NoSuchMethodException ex) {
                throw new ExceptionInInitializerError(
                        "InetAddressAndPort has neither public 'address' field nor 'getAddress()' method");
            }
        }
        EXTRACTOR = resolved;
    }

    private InetAddressAndPortCompat() {}

    /**
     * Extract {@link InetAddress} from an {@link InetAddressAndPort} instance,
     * compatible with both Cassandra 4.0.x and 4.1.x.
     */
    public static InetAddress getAddress(final InetAddressAndPort endpoint) {
        try {
            return EXTRACTOR.extract(endpoint);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException("Failed to extract InetAddress from InetAddressAndPort", e);
        }
    }
}
