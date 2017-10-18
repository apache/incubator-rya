package org.apache.rya.rdftriplestore.provenance.rdf;

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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;

/**
 * Basic representation of Provenance data capture in RDF.
 */
public class BaseProvenanceModel implements RDFProvenanceModel {
	
	private static final ValueFactory vf = SimpleValueFactory.getInstance();
	private static final Resource queryEventType = vf.createIRI("http://rya.com/provenance#QueryEvent");
	private static final IRI atTimeProperty = vf.createIRI("http://www.w3.org/ns/prov#atTime");
	private static final IRI associatedWithUser = vf.createIRI("http://rya.com/provenance#associatedWithUser");
	private static final IRI queryTypeProp = vf.createIRI("http://rya.com/provenance#queryType");
	private static final IRI executedQueryProperty = vf.createIRI("http://rya.com/provenance#executedQuery");
	private static final String queryNameSpace = "http://rya.com/provenance#queryEvent";

	/* (non-Javadoc)
	 * @see org.apache.rya.rdftriplestore.provenance.rdf.RDFProvenanceModel#getStatementsForQuery(java.lang.String, java.lang.String, java.lang.String)
	 */
	public List<Statement> getStatementsForQuery(String query, String user, String queryType) {
		List<Statement> statements = new ArrayList<Statement>();
		// create some statements for the query
		Resource queryEventResource = vf.createIRI(queryNameSpace + UUID.randomUUID().toString());
		Statement queryEventDecl = vf.createStatement(queryEventResource, RDF.TYPE, queryEventType);
		statements.add(queryEventDecl);
		Statement queryEventTime = vf.createStatement(queryEventResource, atTimeProperty, vf.createLiteral(new Date()));
		statements.add(queryEventTime);
		Statement queryUser = vf.createStatement(queryEventResource, associatedWithUser, vf.createLiteral(user));
		statements.add(queryUser);
		Statement executedQuery = vf.createStatement(queryEventResource, executedQueryProperty, vf.createLiteral(query));
		statements.add(executedQuery);
		Statement queryTypeStatement = vf.createStatement(queryEventResource, queryTypeProp, vf.createLiteral(queryType));
		statements.add(queryTypeStatement);
		return statements;
	}

}
