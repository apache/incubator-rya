/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.rya.benchmark.periodic;

import com.beust.jcommander.Parameters;
import com.google.common.base.Objects;

@Parameters(commandNames = { "projection" }, commandDescription = "Run benchmark with a simple projection query.  This requires the Rya PCJ Updater Incremental Join Application to be running.")
public class ProjectionQueryCommand extends BenchmarkOptions {

    @Override
    public String toString() {
        return Objects.toStringHelper(this).toString() + super.toString();
    }
}
