package org.apache.rya.indexing.accumulo.customfunction;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.algebra.evaluation.ValueExprEvaluationException;
import org.openrdf.query.algebra.evaluation.function.Function;

public class CustomSparqlFunction implements Function {

    @Override
    public Value evaluate(ValueFactory valueFactory, Value... arg1) throws ValueExprEvaluationException {
        if (arg1.length == 1) {
            return valueFactory.createLiteral("Hello, " + arg1[0].stringValue());
        } else {
            return valueFactory.createLiteral("Hello");
        }
    }

    @Override
    public String getURI() {
        // TODO Auto-generated method stub
        return "http://example.org#mycustomfunction";
    }

}
