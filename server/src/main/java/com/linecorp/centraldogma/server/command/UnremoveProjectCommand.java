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

package com.linecorp.centraldogma.server.command;

import static java.util.Objects.requireNonNull;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects.ToStringHelper;

import com.linecorp.centraldogma.common.Author;

/**
 * A {@link Command} which is used for restoring a project that was removed before.
 */
public final class UnremoveProjectCommand extends RootCommand<Void> {

    private final String projectName;

    @JsonCreator
    UnremoveProjectCommand(@JsonProperty("timestamp") @Nullable Long timestamp,
                           @JsonProperty("author") @Nullable Author author,
                           @JsonProperty("projectName") String projectName) {
        super(CommandType.UNREMOVE_PROJECT, timestamp, author);
        this.projectName = requireNonNull(projectName, "projectName");
    }

    @JsonProperty
    public String projectName() {
        return projectName;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof UnremoveProjectCommand)) {
            return false;
        }

        final UnremoveProjectCommand that = (UnremoveProjectCommand) obj;
        return super.equals(obj) &&
               projectName.equals(that.projectName);
    }

    @Override
    public int hashCode() {
        return projectName.hashCode() * 31 + super.hashCode();
    }

    @Override
    ToStringHelper toStringHelper() {
        return super.toStringHelper().add("projectName", projectName);
    }
}
