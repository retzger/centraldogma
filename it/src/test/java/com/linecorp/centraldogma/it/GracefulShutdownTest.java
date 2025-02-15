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

package com.linecorp.centraldogma.it;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import com.linecorp.centraldogma.common.Query;
import com.linecorp.centraldogma.common.Revision;
import com.linecorp.centraldogma.common.ShuttingDownException;
import com.linecorp.centraldogma.server.CentralDogmaBuilder;
import com.linecorp.centraldogma.server.GracefulShutdownTimeout;

public class GracefulShutdownTest extends AbstractMultiClientTest {

    @ClassRule
    public static final CentralDogmaRuleWithScaffolding rule = new CentralDogmaRuleWithScaffolding() {
        @Override
        protected void configure(CentralDogmaBuilder builder) {
            super.configure(builder);
            builder.gracefulShutdownTimeout(new GracefulShutdownTimeout(1000, 2000));
        }
    };

    @Rule
    public TestRule globalTimeout = new DisableOnDebug(new Timeout(20, TimeUnit.SECONDS));

    public GracefulShutdownTest(ClientType clientType) {
        super(clientType);
    }

    @Before
    public void startServer() {
        rule.start();
    }

    @Test(timeout = 20000)
    public void testWatchRepositoryGracefulShutdown() throws Exception {
        testGracefulShutdown(client().watchRepository(
                rule.project(), rule.repo1(), Revision.HEAD, "/**", 60000));
    }

    @Test(timeout = 20000)
    public void testWatchFileGracefulShutdown() throws Exception {
        testGracefulShutdown(client().watchFile(
                rule.project(), rule.repo1(), Revision.HEAD, Query.ofJson("/test.json"), 60000));
    }

    private static void testGracefulShutdown(CompletableFuture<?> future) throws Exception {
        // Wait a little bit so that we do not start to stop the server before the watch operation is accepted.
        Thread.sleep(500);
        rule.stopAsync();

        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(ShuttingDownException.class);
    }
}
