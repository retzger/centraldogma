/*
 * Copyright 2018 LINE Corporation
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
package com.linecorp.centraldogma.server.internal.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CompletionException;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.linecorp.centraldogma.server.auth.Session;

public class FileBasedSessionManagerTest {

    @ClassRule
    public static TemporaryFolder rootDir = new TemporaryFolder();

    @Test
    public void shouldDoBasicOperations() throws Exception {
        final FileBasedSessionManager manager = new FileBasedSessionManager(rootDir.newFolder().toPath(), null);
        final Session session = new Session(manager.generateSessionId(), "username", Duration.ofHours(1));
        manager.create(session).join();
        assertThat(manager.get(session.id()).join())
                .isEqualToIgnoringGivenFields(session, "rawSession");

        final Session updatedSession =
                new Session(session.id(), "username2", Duration.ofHours(2));
        manager.update(updatedSession).join();
        assertThat(manager.get(updatedSession.id()).join())
                .isEqualToIgnoringGivenFields(updatedSession, "rawSession");

        manager.delete(updatedSession.id()).join();
        assertThat(manager.get(updatedSession.id()).join()).isNull();
    }

    @Test
    public void shouldDeleteExpiredSessions() throws Exception {
        final FileBasedSessionManager manager =
                new FileBasedSessionManager(rootDir.newFolder().toPath(), "*/2 * * ? * *");

        final Session session =
                new Session(manager.generateSessionId(), "username", Duration.ofSeconds(5));
        manager.create(session).join();

        await().untilAsserted(() -> assertThat(manager.get(session.id()).join()).isNull());
    }

    @Test
    public void invalidSessionIds() throws Exception {
        final FileBasedSessionManager manager = new FileBasedSessionManager(rootDir.newFolder().toPath(), null);
        assertThat(manager.get("anonymous").join()).isNull();
        assertThat(manager.exists("anonymous").join()).isFalse();

        // Other operations such as create, update and delete should fail.
        final Session session = new Session("anonymous", "username", Duration.ofHours(1));
        assertThatThrownBy(() -> manager.create(session).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .satisfies(cause -> assertThat(cause.getCause()).hasMessageContaining("sessionId:"));

        assertThatThrownBy(() -> manager.update(session).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .satisfies(cause -> assertThat(cause.getCause()).hasMessageContaining("sessionId:"));

        assertThatThrownBy(() -> manager.delete("anonymous").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .satisfies(cause -> assertThat(cause.getCause()).hasMessageContaining("sessionId:"));
    }
}
