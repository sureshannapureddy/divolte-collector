package io.divolte.server.geo2ip;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import javax.annotation.ParametersAreNonnullByDefault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;

import static com.google.common.io.Files.*;

@ParametersAreNonnullByDefault
public class DatabaseLookupService implements LookupService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseLookupService.class);

    private final DatabaseReader databaseReader;

    public DatabaseLookupService(final Path externalLocation) throws IOException {
        databaseReader = loadExternalDatabase(externalLocation);
    }

    private static DatabaseReader loadExternalDatabase(final Path location) throws IOException {
        /*
         * We have 2 strategies here.
         * 1) If compressed, we load via a stream (which means into memory).
         * 2) If it's not compressed, we pass a File in directly which allows DatabaseReader
         *    to mmap(2) the file.
         */
        final DatabaseReader reader;
        if ("gz".equals(getFileExtension(location.toString()))) {
            try (final InputStream rawStream = Files.newInputStream(location);
                 final InputStream bufferedStream = new BufferedInputStream(rawStream);
                 final InputStream dbStream = new GZIPInputStream(bufferedStream)) {
                logger.debug("Loading compressed GeoIP database: {}", location);
                reader = new DatabaseReader.Builder(dbStream).build();
            }
        } else {
            logger.debug("Loading GeoIP database: {}", location);
            reader = new DatabaseReader.Builder(location.toFile()).build();
        }
        logger.info("Loaded GeoIP database: {}", location);
        return reader;
    }

    @Override
    public void close() throws IOException {
        logger.debug("Closed GeoIP database.");
        databaseReader.close();
    }

    @Override
    public Optional<CityResponse> lookup(final InetAddress address) {
        Optional<CityResponse> result;
        try {
            result = Optional.of(databaseReader.city(address));
            logger.debug("Lookup result for {}: {}", address, result);
        } catch (final GeoIp2Exception e) {
            // This can happen during normal operation.
            if (logger.isDebugEnabled()) {
                logger.debug("Ignoring failure to lookup address: " + address, e);
            }
            result = Optional.empty();
        } catch (final IOException e) {
            logger.warn("Lookup failed for address: " + address, e);
            result = Optional.empty();
        }
        return result;
    }
}
