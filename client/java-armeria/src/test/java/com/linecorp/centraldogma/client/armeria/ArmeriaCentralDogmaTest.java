/*
 * Copyright 2019 LINE Corporation
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
package com.linecorp.centraldogma.client.armeria;

import static com.linecorp.centraldogma.client.armeria.ArmeriaCentralDogma.encodePathPattern;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class ArmeriaCentralDogmaTest {
    @Test
    public void testEncodePathPattern() {
        assertThat(encodePathPattern("/")).isEqualTo("/");
        assertThat(encodePathPattern(" ")).isEqualTo("%20");
        assertThat(encodePathPattern("  ")).isEqualTo("%20%20");
        assertThat(encodePathPattern("a b")).isEqualTo("a%20b");
        assertThat(encodePathPattern(" a ")).isEqualTo("%20a%20");

        // No new string has to be created when escaping is not necessary.
        final String pathPatternThatDoesNotNeedEscaping = "/*.zip,/**/*.jar";
        assertThat(encodePathPattern(pathPatternThatDoesNotNeedEscaping))
                .isSameAs(pathPatternThatDoesNotNeedEscaping);
    }
}
