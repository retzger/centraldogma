/*
 * Copyright 2017 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.centraldogma.testing;

import java.io.IOError;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import com.spotify.futures.CompletableFutures;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.util.Exceptions;
import com.linecorp.armeria.server.ServerPort;
import com.linecorp.centraldogma.client.CentralDogma;
import com.linecorp.centraldogma.client.armeria.AbstractArmeriaCentralDogmaBuilder;
import com.linecorp.centraldogma.client.armeria.ArmeriaCentralDogmaBuilder;
import com.linecorp.centraldogma.client.armeria.legacy.LegacyCentralDogmaBuilder;
import com.linecorp.centraldogma.server.CentralDogmaBuilder;
import com.linecorp.centraldogma.server.GracefulShutdownTimeout;
import com.linecorp.centraldogma.server.MirroringService;
import com.linecorp.centraldogma.server.TlsConfig;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.NetUtil;

/**
 * JUnit {@link Rule} that starts an embedded Central Dogma server.
 *
 * <pre>{@code
 * > public class MyTest {
 * >     @ClassRule
 * >     public static final CentralDogmaRule rule = new CentralDogmaRule();
 * >
 * >     @Test
 * >     public void test() throws Exception {
 * >         CentralDogma dogma = rule.client();
 * >         dogma.push(...).join();
 * >         ...
 * >     }
 * > }
 * }</pre>
 */
public class CentralDogmaRule extends TemporaryFolder {

    private static final InetSocketAddress TEST_PORT = new InetSocketAddress(NetUtil.LOCALHOST4, 0);

    private final boolean useTls;
    @Nullable
    private volatile com.linecorp.centraldogma.server.CentralDogma dogma;
    @Nullable
    private volatile CentralDogma client;
    @Nullable
    private volatile CentralDogma legacyClient;
    @Nullable
    private volatile WebClient webClient;
    @Nullable
    private volatile InetSocketAddress serverAddress;

    public CentralDogmaRule() {
        this(false);
    }

    public CentralDogmaRule(boolean useTls) {
        this.useTls = useTls;
    }

    /**
     * Returns whether the server is running over TLS or not.
     */
    public boolean useTls() {
        return useTls;
    }

    /**
     * Returns the server.
     *
     * @throws IllegalStateException if Central Dogma did not start yet
     */
    public final com.linecorp.centraldogma.server.CentralDogma dogma() {
        final com.linecorp.centraldogma.server.CentralDogma dogma = this.dogma;
        if (dogma == null) {
            throw new IllegalStateException("Central Dogma not available");
        }
        return dogma;
    }

    /**
     * Returns the {@link MirroringService} of the server.
     *
     * @throws IllegalStateException if Central Dogma did not start yet
     */
    public final MirroringService mirroringService() {
        return dogma().mirroringService().get();
    }

    /**
     * Returns the HTTP-based {@link CentralDogma} client.
     *
     * @throws IllegalStateException if Central Dogma did not start yet
     */
    public final CentralDogma client() {
        final CentralDogma client = this.client;
        if (client == null) {
            throw new IllegalStateException("Central Dogma client not available");
        }
        return client;
    }

    /**
     * Returns the Thrift-based {@link CentralDogma} client.
     *
     * @throws IllegalStateException if Central Dogma did not start yet
     */
    public final CentralDogma legacyClient() {
        final CentralDogma legacyClient = this.legacyClient;
        if (legacyClient == null) {
            throw new IllegalStateException("Central Dogma not started");
        }
        return legacyClient;
    }

    /**
     * Returns the HTTP client.
     *
     * @throws IllegalStateException if Central Dogma did not start yet
     */
    public final WebClient httpClient() {
        final WebClient webClient = this.webClient;
        if (webClient == null) {
            throw new IllegalStateException("Central Dogma not started");
        }
        return webClient;
    }

    /**
     * Returns the server address.
     *
     * @throws IllegalStateException if Central Dogma did not start yet
     */
    public final InetSocketAddress serverAddress() {
        final InetSocketAddress serverAddress = this.serverAddress;
        if (serverAddress == null) {
            throw new IllegalStateException("Central Dogma not started");
        }
        return serverAddress;
    }

    /**
     * Starts an embedded server with {@link #start()} and calls {@link #scaffold(CentralDogma)}.
     */
    @Override
    protected void before() throws Throwable {
        super.before();
        start();
        final CentralDogma client = this.client;
        assert client != null;
        assert legacyClient != null;
        scaffold(client);
    }

    /**
     * Creates a new server, configures it with {@link #configure(CentralDogmaBuilder)} and starts the server.
     * Note that you don't need to call this method if you did not stop the server with {@link #stop()},
     * because the server is automatically started up by JUnit.
     */
    public final void start() {
        startAsync().join();
    }

    /**
     * Creates a new server, configures it with {@link #configure(CentralDogmaBuilder)} and starts the server
     * asynchronously.
     */
    public final CompletableFuture<Void> startAsync() {
        // Create the root folder first if not.
        try {
            getRoot();
        } catch (IllegalStateException unused) {
            try {
                create();
            } catch (IOException e) {
                return CompletableFutures.exceptionallyCompletedFuture(e);
            }
        }

        final CentralDogmaBuilder builder = new CentralDogmaBuilder(getRoot())
                .port(TEST_PORT, useTls ? SessionProtocol.HTTPS : SessionProtocol.HTTP)
                .webAppEnabled(false)
                .mirroringEnabled(false)
                .gracefulShutdownTimeout(new GracefulShutdownTimeout(0, 0));

        if (useTls) {
            try {
                final SelfSignedCertificate ssc = new SelfSignedCertificate();
                builder.tls(new TlsConfig(ssc.certificate(), ssc.privateKey(), null));
            } catch (Exception e) {
                Exceptions.throwUnsafely(e);
            }
        }

        configure(builder);

        final com.linecorp.centraldogma.server.CentralDogma dogma = builder.build();
        this.dogma = dogma;
        return dogma.start().thenRun(() -> {
            final Optional<ServerPort> activePort = dogma.activePort();
            if (!activePort.isPresent()) {
                // Stopped already.
                return;
            }

            final InetSocketAddress serverAddress = activePort.get().localAddress();
            this.serverAddress = serverAddress;

            final ArmeriaCentralDogmaBuilder clientBuilder = new ArmeriaCentralDogmaBuilder();
            final LegacyCentralDogmaBuilder legacyClientBuilder = new LegacyCentralDogmaBuilder();

            configureClientCommon(clientBuilder);
            configureClientCommon(legacyClientBuilder);
            configureClient(clientBuilder);
            configureClient(legacyClientBuilder);

            try {
                client = clientBuilder.build();
                legacyClient = legacyClientBuilder.build();
            } catch (UnknownHostException e) {
                // Should never reach here.
                throw new IOError(e);
            }
            webClient = WebClient.of(
                    "h2c://" + serverAddress.getHostString() + ':' + serverAddress.getPort());
        });
    }

    private void configureClientCommon(AbstractArmeriaCentralDogmaBuilder<?> builder) {
        final InetSocketAddress serverAddress = this.serverAddress;
        assert serverAddress != null;
        builder.host(serverAddress.getHostString(), serverAddress.getPort());

        if (useTls) {
            builder.useTls();
            builder.clientFactory(
                    ClientFactory.builder()
                                 .sslContextCustomizer(
                                         b -> b.trustManager(InsecureTrustManagerFactory.INSTANCE))
                                 .build());
        }
    }

    /**
     * Override this method to configure the server.
     */
    protected void configure(CentralDogmaBuilder builder) {}

    /**
     * Override this method to configure the HTTP-based {@link CentralDogma} client builder.
     */
    protected void configureClient(ArmeriaCentralDogmaBuilder builder) {}

    /**
     * Override this method to configure the Thrift-based {@link CentralDogma} client builder.
     */
    protected void configureClient(LegacyCentralDogmaBuilder builder) {}

    /**
     * Override this method to perform the initial updates on the server, such as creating a repository and
     * populating sample data.
     */
    protected void scaffold(CentralDogma client) {}

    /**
     * Stops the server and deletes the temporary files created by the server.
     */
    @Override
    protected void after() {
        stopAsync().whenComplete((unused1, unused2) -> delete());
    }

    /**
     * Stops the server and deletes the temporary files created by the server. Note that you don't usually need
     * to call this method manually because the server is automatically stopped at the end by JUnit.
     */
    public final void stop() {
        stopAsync().join();
    }

    /**
     * Stops the server and deletes the temporary files created by the server. Note that you don't usually need
     * to call this method manually because the server is automatically stopped at the end by JUnit.
     */
    public final CompletableFuture<Void> stopAsync() {
        final com.linecorp.centraldogma.server.CentralDogma dogma = this.dogma;
        this.dogma = null;

        if (dogma != null) {
            return dogma.stop();
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }
}
