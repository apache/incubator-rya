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
package org.apache.rya.indexing.external.tupleSet;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.client.admin.TableOperations;
import org.apache.hadoop.conf.Configuration;
import org.apache.rya.accumulo.AccumuloRdfConfiguration;
import org.apache.rya.api.RdfTripleStoreConfiguration;
import org.apache.rya.api.persist.RyaDAOException;
import org.apache.rya.api.resolver.RyaTypeResolverException;
import org.apache.rya.indexing.accumulo.ConfigUtils;
import org.apache.rya.indexing.external.PcjIntegrationTestingUtil;
import org.apache.rya.indexing.pcj.matching.QueryVariableNormalizer;
import org.apache.rya.indexing.pcj.storage.PcjException;
import org.apache.rya.indexing.pcj.storage.accumulo.PcjTableNameFactory;
import org.apache.rya.rdftriplestore.RyaSailRepository;
import org.apache.rya.rdftriplestore.inference.InferenceEngineException;
import org.apache.rya.sail.config.RyaSailFactory;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.LiteralImpl;
import org.eclipse.rdf4j.model.impl.NumericLiteralImpl;
import org.eclipse.rdf4j.model.impl.StatementImpl;
import org.eclipse.rdf4j.model.impl.URIImpl;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.Projection;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryBindingSet;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.sail.SailException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AccumuloIndexSetTest {

     protected static Connector accumuloConn = null;
     protected RyaSailRepository ryaRepo = null;
     protected RepositoryConnection ryaConn = null;
     protected Configuration conf = getConf();
     protected String prefix = "rya_";

    @Before
    public void init() throws AccumuloException, AccumuloSecurityException,
        RyaDAOException, RepositoryException, TableNotFoundException,
        InferenceEngineException, NumberFormatException, UnknownHostException, SailException {
        accumuloConn = ConfigUtils.getConnector(conf);
        final TableOperations ops = accumuloConn.tableOperations();
        if(ops.exists(prefix+"INDEX_"+ "testPcj")) {
            ops.delete(prefix+"INDEX_"+ "testPcj");
        }
        ryaRepo = new RyaSailRepository(RyaSailFactory.getInstance(conf));
        ryaConn = ryaRepo.getConnection();
    }

    /**
     * TODO doc
     * @throws QueryEvaluationException
     * @throws SailException
     * @throws MalformedQueryException
     * @throws AccumuloSecurityException
     * @throws AccumuloException
     */
    @Test
    public void accumuloIndexSetTestWithEmptyBindingSet() throws RepositoryException, PcjException, TableNotFoundException,
    RyaTypeResolverException, MalformedQueryException, SailException, QueryEvaluationException, AccumuloException, AccumuloSecurityException {
        // Load some Triples into Rya.
        final Set<Statement> triples = new HashSet<>();
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://hasAge"), new NumericLiteralImpl(14, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://hasAge"), new NumericLiteralImpl(16, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://hasAge"), new NumericLiteralImpl(12, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://hasAge"), new NumericLiteralImpl(43, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );

        for(final Statement triple : triples) {
            ryaConn.add(triple);
        }

        // Create a PCJ table will include those triples in its results.
        final String sparql =
                "SELECT ?name ?age " +
                "{" +
                  "FILTER(?age < 30) ." +
                  "?name <http://hasAge> ?age." +
                  "?name <http://playsSport> \"Soccer\" " +
                "}";

        final String pcjTableName = new PcjTableNameFactory().makeTableName(prefix, "testPcj");
        // Create and populate the PCJ table.
        PcjIntegrationTestingUtil.createAndPopulatePcj(ryaConn, accumuloConn, pcjTableName, sparql, new String[]{"name", "age"}, Optional.absent());

        final AccumuloIndexSet ais = new AccumuloIndexSet(conf, pcjTableName);

        final CloseableIteration<BindingSet, QueryEvaluationException> results = ais.evaluate(new QueryBindingSet());
        final Set<BindingSet> fetchedResults = new HashSet<BindingSet>();
        while(results.hasNext()) {
            fetchedResults.add(results.next());
        }
        // Ensure the expected results match those that were stored.
        final QueryBindingSet alice = new QueryBindingSet();
        alice.addBinding("name", new URIImpl("http://Alice"));
        alice.addBinding("age", new NumericLiteralImpl(14, XMLSchema.INTEGER));

        final QueryBindingSet bob = new QueryBindingSet();
        bob.addBinding("name", new URIImpl("http://Bob"));
        bob.addBinding("age", new NumericLiteralImpl(16, XMLSchema.INTEGER));

        final QueryBindingSet charlie = new QueryBindingSet();
        charlie.addBinding("name", new URIImpl("http://Charlie"));
        charlie.addBinding("age", new NumericLiteralImpl(12, XMLSchema.INTEGER));

        final Set<BindingSet> expectedResults = Sets.newHashSet(alice, bob, charlie);
        Assert.assertEquals(expectedResults, fetchedResults);
    }

    /**
     * TODO doc
     * @throws QueryEvaluationException
     * @throws SailException
     * @throws MalformedQueryException
     * @throws AccumuloSecurityException
     * @throws AccumuloException
     */
    @Test
    public void accumuloIndexSetTestWithBindingSet() throws RepositoryException, PcjException, TableNotFoundException,
    RyaTypeResolverException, MalformedQueryException, SailException, QueryEvaluationException, AccumuloException, AccumuloSecurityException {
        // Load some Triples into Rya.
        final Set<Statement> triples = new HashSet<>();
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://hasAge"), new NumericLiteralImpl(14, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://hasAge"), new NumericLiteralImpl(16, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://hasAge"), new NumericLiteralImpl(12, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://hasAge"), new NumericLiteralImpl(43, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );

        for(final Statement triple : triples) {
            ryaConn.add(triple);
        }

        // Create a PCJ table will include those triples in its results.
        final String sparql =
                "SELECT ?name ?age " +
                "{" +
                  "FILTER(?age < 30) ." +
                  "?name <http://hasAge> ?age." +
                  "?name <http://playsSport> \"Soccer\" " +
                "}";

        final String pcjTableName = new PcjTableNameFactory().makeTableName(prefix, "testPcj");

        // Create and populate the PCJ table.
        PcjIntegrationTestingUtil.createAndPopulatePcj(ryaConn, accumuloConn, pcjTableName, sparql, new String[]{"name", "age"}, Optional.absent());

        final AccumuloIndexSet ais = new AccumuloIndexSet(conf, pcjTableName);

        final QueryBindingSet bs = new QueryBindingSet();
        bs.addBinding("name",new URIImpl("http://Alice"));
        bs.addBinding("location",new URIImpl("http://Virginia"));

        final CloseableIteration<BindingSet, QueryEvaluationException> results = ais.evaluate(bs);

        bs.addBinding("age",new NumericLiteralImpl(14, XMLSchema.INTEGER));
        Assert.assertEquals(bs, results.next());
    }

    @Test
    public void accumuloIndexSetTestWithTwoBindingSets() throws RepositoryException, PcjException, TableNotFoundException,
    RyaTypeResolverException, MalformedQueryException, SailException, QueryEvaluationException, AccumuloException, AccumuloSecurityException {
        // Load some Triples into Rya.
        final Set<Statement> triples = new HashSet<>();
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://hasAge"), new NumericLiteralImpl(14, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://hasAge"), new NumericLiteralImpl(16, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://hasAge"), new NumericLiteralImpl(12, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://hasAge"), new NumericLiteralImpl(43, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );

        for(final Statement triple : triples) {
            ryaConn.add(triple);
        }

        // Create a PCJ table will include those triples in its results.
        final String sparql =
                "SELECT ?name ?age " +
                "{" +
                  "FILTER(?age < 30) ." +
                  "?name <http://hasAge> ?age." +
                  "?name <http://playsSport> \"Soccer\" " +
                "}";

        final String pcjTableName = new PcjTableNameFactory().makeTableName(prefix, "testPcj");

        // Create and populate the PCJ table.
        PcjIntegrationTestingUtil.createAndPopulatePcj(ryaConn, accumuloConn, pcjTableName, sparql, new String[]{"name", "age"}, Optional.absent());

        final AccumuloIndexSet ais = new AccumuloIndexSet(conf, pcjTableName);

        final QueryBindingSet bs = new QueryBindingSet();
        bs.addBinding("birthDate",new LiteralImpl("1983-03-17",new URIImpl("http://www.w3.org/2001/XMLSchema#date")));
        bs.addBinding("name",new URIImpl("http://Alice"));

        final QueryBindingSet bs2 = new QueryBindingSet();
        bs2.addBinding("birthDate",new LiteralImpl("1983-04-18",new URIImpl("http://www.w3.org/2001/XMLSchema#date")));
        bs2.addBinding("name",new URIImpl("http://Bob"));

        final Set<BindingSet> bSets = Sets.newHashSet(bs,bs2);

        final CloseableIteration<BindingSet, QueryEvaluationException> results = ais.evaluate(bSets);

        final QueryBindingSet alice = new QueryBindingSet();
        alice.addBinding("name", new URIImpl("http://Alice"));
        alice.addBinding("age", new NumericLiteralImpl(14, XMLSchema.INTEGER));
        alice.addBinding("birthDate", new LiteralImpl("1983-03-17",new URIImpl("http://www.w3.org/2001/XMLSchema#date")));

        final QueryBindingSet bob = new QueryBindingSet();
        bob.addBinding("name", new URIImpl("http://Bob"));
        bob.addBinding("age", new NumericLiteralImpl(16, XMLSchema.INTEGER));
        bob.addBinding("birthDate", new LiteralImpl("1983-04-18",new URIImpl("http://www.w3.org/2001/XMLSchema#date")));

        final Set<BindingSet> fetchedResults = new HashSet<>();
        while(results.hasNext()) {
            final BindingSet next = results.next();
            System.out.println(next);
            fetchedResults.add(next);
        }

        Assert.assertEquals(Sets.<BindingSet>newHashSet(alice,bob), fetchedResults);
    }

    @Test
    public void accumuloIndexSetTestWithNoBindingSet() throws RepositoryException, PcjException, TableNotFoundException,
    RyaTypeResolverException, MalformedQueryException, SailException, QueryEvaluationException, AccumuloException, AccumuloSecurityException {
        // Load some Triples into Rya.
        final Set<Statement> triples = new HashSet<>();
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://hasAge"), new NumericLiteralImpl(14, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://hasAge"), new NumericLiteralImpl(16, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://hasAge"), new NumericLiteralImpl(12, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://hasAge"), new NumericLiteralImpl(43, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );

        for(final Statement triple : triples) {
            ryaConn.add(triple);
        }

        // Create a PCJ table will include those triples in its results.
        final String sparql =
                "SELECT ?name ?age " +
                "{" +
                  "FILTER(?age < 30) ." +
                  "?name <http://hasAge> ?age." +
                  "?name <http://playsSport> \"Soccer\" " +
                "}";

        final String pcjTableName = new PcjTableNameFactory().makeTableName(prefix, "testPcj");

        // Create and populate the PCJ table.
        PcjIntegrationTestingUtil.createAndPopulatePcj(ryaConn, accumuloConn, pcjTableName, sparql, new String[]{"name", "age"}, Optional.absent());

        final AccumuloIndexSet ais = new AccumuloIndexSet(conf, pcjTableName);

        final CloseableIteration<BindingSet, QueryEvaluationException> results = ais.evaluate(new HashSet<BindingSet>());

        Assert.assertEquals(false, results.hasNext());

    }

    @Test
    public void accumuloIndexSetTestWithDirectProductBindingSet() throws RepositoryException, PcjException, TableNotFoundException,
    RyaTypeResolverException, MalformedQueryException, SailException, QueryEvaluationException, AccumuloException, AccumuloSecurityException {
        // Load some Triples into Rya.
        final Set<Statement> triples = new HashSet<>();
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://hasAge"), new NumericLiteralImpl(14, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://hasAge"), new NumericLiteralImpl(16, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://hasAge"), new NumericLiteralImpl(12, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://hasAge"), new NumericLiteralImpl(43, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );

        for(final Statement triple : triples) {
            ryaConn.add(triple);
        }

        // Create a PCJ table will include those triples in its results.
        final String sparql =
                "SELECT ?name ?age " +
                "{" +
                  "FILTER(?age < 30) ." +
                  "?name <http://hasAge> ?age." +
                  "?name <http://playsSport> \"Soccer\" " +
                "}";

        final String pcjTableName = new PcjTableNameFactory().makeTableName(prefix, "testPcj");

        // Create and populate the PCJ table.
        PcjIntegrationTestingUtil.createAndPopulatePcj(ryaConn, accumuloConn, pcjTableName, sparql, new String[]{"name", "age"}, Optional.absent());

        final AccumuloIndexSet ais = new AccumuloIndexSet(conf, pcjTableName);

        final QueryBindingSet bs = new QueryBindingSet();
        bs.addBinding("birthDate",new LiteralImpl("1983-03-17",new URIImpl("http://www.w3.org/2001/XMLSchema#date")));
        bs.addBinding("location",new URIImpl("http://Virginia"));

        final CloseableIteration<BindingSet, QueryEvaluationException> results = ais.evaluate(bs);

        final QueryBindingSet alice = new QueryBindingSet();
        alice.addBinding("name", new URIImpl("http://Alice"));
        alice.addBinding("age", new NumericLiteralImpl(14, XMLSchema.INTEGER));
        alice.addAll(bs);

        final QueryBindingSet bob = new QueryBindingSet();
        bob.addBinding("name", new URIImpl("http://Bob"));
        bob.addBinding("age", new NumericLiteralImpl(16, XMLSchema.INTEGER));
        bob.addAll(bs);

        final QueryBindingSet charlie = new QueryBindingSet();
        charlie.addBinding("name", new URIImpl("http://Charlie"));
        charlie.addBinding("age", new NumericLiteralImpl(12, XMLSchema.INTEGER));
        charlie.addAll(bs);

        final Set<BindingSet> fetchedResults = new HashSet<>();
        while(results.hasNext()) {
            fetchedResults.add(results.next());
        }
        Assert.assertEquals(3,fetchedResults.size());
        Assert.assertEquals(Sets.<BindingSet>newHashSet(alice,bob,charlie), fetchedResults);
    }

    @Test
    public void accumuloIndexSetTestWithTwoDirectProductBindingSet() throws RepositoryException, PcjException, TableNotFoundException,
    RyaTypeResolverException, MalformedQueryException, SailException, QueryEvaluationException, AccumuloException, AccumuloSecurityException {
        // Load some Triples into Rya.
        final Set<Statement> triples = new HashSet<>();
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://hasAge"), new NumericLiteralImpl(14, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://hasAge"), new NumericLiteralImpl(16, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://hasAge"), new NumericLiteralImpl(12, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://hasAge"), new NumericLiteralImpl(43, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );

        for(final Statement triple : triples) {
            ryaConn.add(triple);
        }

        // Create a PCJ table will include those triples in its results.
        final String sparql =
                "SELECT ?name ?age " +
                "{" +
                  "FILTER(?age < 30) ." +
                  "?name <http://hasAge> ?age." +
                  "?name <http://playsSport> \"Soccer\" " +
                "}";

        final String pcjTableName = new PcjTableNameFactory().makeTableName(prefix, "testPcj");

        // Create and populate the PCJ table.
        PcjIntegrationTestingUtil.createAndPopulatePcj(ryaConn, accumuloConn, pcjTableName, sparql, new String[]{"name", "age"}, Optional.absent());

        final AccumuloIndexSet ais = new AccumuloIndexSet(conf, pcjTableName);

        final QueryBindingSet bs = new QueryBindingSet();
        bs.addBinding("birthDate",new LiteralImpl("1983-03-17",new URIImpl("http://www.w3.org/2001/XMLSchema#date")));
        bs.addBinding("location",new URIImpl("http://Virginia"));

        final QueryBindingSet bs2 = new QueryBindingSet();
        bs2.addBinding("birthDate",new LiteralImpl("1983-04-18",new URIImpl("http://www.w3.org/2001/XMLSchema#date")));
        bs2.addBinding("location",new URIImpl("http://Georgia"));

        final Set<BindingSet> bSets = Sets.newHashSet(bs,bs2);

        final CloseableIteration<BindingSet, QueryEvaluationException> results = ais.evaluate(bSets);

        final QueryBindingSet alice1 = new QueryBindingSet();
        alice1.addBinding("name", new URIImpl("http://Alice"));
        alice1.addBinding("age", new NumericLiteralImpl(14, XMLSchema.INTEGER));
        alice1.addAll(bs);

        final QueryBindingSet bob1 = new QueryBindingSet();
        bob1.addBinding("name", new URIImpl("http://Bob"));
        bob1.addBinding("age", new NumericLiteralImpl(16, XMLSchema.INTEGER));
        bob1.addAll(bs);

        final QueryBindingSet charlie1 = new QueryBindingSet();
        charlie1.addBinding("name", new URIImpl("http://Charlie"));
        charlie1.addBinding("age", new NumericLiteralImpl(12, XMLSchema.INTEGER));
        charlie1.addAll(bs);

        final QueryBindingSet alice2 = new QueryBindingSet();
        alice2.addBinding("name", new URIImpl("http://Alice"));
        alice2.addBinding("age", new NumericLiteralImpl(14, XMLSchema.INTEGER));
        alice2.addAll(bs2);

        final QueryBindingSet bob2 = new QueryBindingSet();
        bob2.addBinding("name", new URIImpl("http://Bob"));
        bob2.addBinding("age", new NumericLiteralImpl(16, XMLSchema.INTEGER));
        bob2.addAll(bs2);

        final QueryBindingSet charlie2 = new QueryBindingSet();
        charlie2.addBinding("name", new URIImpl("http://Charlie"));
        charlie2.addBinding("age", new NumericLiteralImpl(12, XMLSchema.INTEGER));
        charlie2.addAll(bs2);

        final Set<BindingSet> fetchedResults = new HashSet<>();
        while(results.hasNext()) {
            final BindingSet next = results.next();
            System.out.println(next);
            fetchedResults.add(next);
        }

        Assert.assertEquals(Sets.<BindingSet>newHashSet(alice1,bob1,charlie1,alice2,bob2,charlie2), fetchedResults);
    }

    @Test
    public void accumuloIndexSetTestWithTwoDirectProductBindingSetsWithMapping() throws RepositoryException, PcjException, TableNotFoundException,
    RyaTypeResolverException, MalformedQueryException, SailException, QueryEvaluationException, AccumuloException, AccumuloSecurityException {
        // Load some Triples into Rya.
        final Set<Statement> triples = new HashSet<>();
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://hasAge"), new NumericLiteralImpl(14, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://hasAge"), new NumericLiteralImpl(16, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://hasAge"), new NumericLiteralImpl(12, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://hasAge"), new NumericLiteralImpl(43, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );

        for(final Statement triple : triples) {
            ryaConn.add(triple);
        }

        // Create a PCJ table will include those triples in its results.
        final String sparql =
                "SELECT ?name ?age " +
                "{" +
                  "FILTER(?age < 30) ." +
                  "?name <http://hasAge> ?age." +
                  "?name <http://playsSport> \"Soccer\" " +
                "}";

        final String pcjTableName = new PcjTableNameFactory().makeTableName(prefix, "testPcj");

        // Create and populate the PCJ table.
        PcjIntegrationTestingUtil.createAndPopulatePcj(ryaConn, accumuloConn, pcjTableName, sparql, new String[]{"name", "age"}, Optional.absent());

        final String sparql2 =
                "SELECT ?x ?y " +
                "{" +
                  "FILTER(?y < 30) ." +
                  "?x <http://hasAge> ?y." +
                  "?x <http://playsSport> \"Soccer\" " +
                "}";

        final SPARQLParser p = new SPARQLParser();
        final ParsedQuery pq = p.parseQuery(sparql2, null);

        final Map<String,String> map = new HashMap<>();
        map.put("x", "name");
        map.put("y", "age");
        final AccumuloIndexSet ais = new AccumuloIndexSet(conf, pcjTableName);
        ais.setProjectionExpr((Projection) pq.getTupleExpr());
        ais.setTableVarMap(map);
        ais.setSupportedVariableOrderMap(Lists.newArrayList("x;y","y;x"));

        final QueryBindingSet bs = new QueryBindingSet();
        bs.addBinding("birthDate",new LiteralImpl("1983-03-17",new URIImpl("http://www.w3.org/2001/XMLSchema#date")));
        bs.addBinding("x",new URIImpl("http://Alice"));

        final QueryBindingSet bs2 = new QueryBindingSet();
        bs2.addBinding("birthDate",new LiteralImpl("1983-04-18",new URIImpl("http://www.w3.org/2001/XMLSchema#date")));
        bs2.addBinding("x",new URIImpl("http://Bob"));

        final Set<BindingSet> bSets = Sets.newHashSet(bs,bs2);

        final CloseableIteration<BindingSet, QueryEvaluationException> results = ais.evaluate(bSets);

        final QueryBindingSet alice = new QueryBindingSet();
        alice.addBinding("x", new URIImpl("http://Alice"));
        alice.addBinding("y", new NumericLiteralImpl(14, XMLSchema.INTEGER));
        alice.addBinding("birthDate", new LiteralImpl("1983-03-17",new URIImpl("http://www.w3.org/2001/XMLSchema#date")));

        final QueryBindingSet bob = new QueryBindingSet();
        bob.addBinding("x", new URIImpl("http://Bob"));
        bob.addBinding("y", new NumericLiteralImpl(16, XMLSchema.INTEGER));
        bob.addBinding("birthDate", new LiteralImpl("1983-04-18",new URIImpl("http://www.w3.org/2001/XMLSchema#date")));


        final Set<BindingSet> fetchedResults = new HashSet<>();
        while(results.hasNext()) {
            final BindingSet next = results.next();
            System.out.println(next);
            fetchedResults.add(next);
        }

        Assert.assertEquals(Sets.<BindingSet>newHashSet(alice,bob), fetchedResults);
    }

    @Test
    public void accumuloIndexSetTestWithTwoDirectProductBindingSetsWithConstantMapping() throws Exception {
        // Load some Triples into Rya.
        final Set<Statement> triples = new HashSet<>();
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://hasAge"), new NumericLiteralImpl(14, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://hasAge"), new NumericLiteralImpl(16, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://hasAge"), new NumericLiteralImpl(12, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Charlie"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://hasAge"), new NumericLiteralImpl(43, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Eve"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );

        for(final Statement triple : triples) {
            ryaConn.add(triple);
        }

        // Create a PCJ table will include those triples in its results.
        final String sparql =
                "SELECT ?name ?age " +
                "{" +
                  "?name <http://hasAge> ?age." +
                  "?name <http://playsSport> \"Soccer\" " +
                "}";

        final String pcjTableName = new PcjTableNameFactory().makeTableName(prefix, "testPcj");

        // Create and populate the PCJ table.
        PcjIntegrationTestingUtil.createAndPopulatePcj(ryaConn, accumuloConn, pcjTableName, sparql, new String[]{"name", "age"}, Optional.absent());

        final String sparql2 =
                "SELECT ?x " +
                "{" +
                  "?x <http://hasAge> 16 ." +
                  "?x <http://playsSport> \"Soccer\" " +
                "}";

        final SPARQLParser p = new SPARQLParser();
        final ParsedQuery pq1 = p.parseQuery(sparql, null);
        final ParsedQuery pq2 = p.parseQuery(sparql2, null);

        final AccumuloIndexSet ais = new AccumuloIndexSet(conf, pcjTableName);
        ais.setProjectionExpr((Projection) QueryVariableNormalizer.getNormalizedIndex(pq2.getTupleExpr(), pq1.getTupleExpr()).get(0));

        final QueryBindingSet bs = new QueryBindingSet();
        bs.addBinding("birthDate",new LiteralImpl("1983-03-17",new URIImpl("http://www.w3.org/2001/XMLSchema#date")));
        bs.addBinding("x",new URIImpl("http://Alice"));

        final QueryBindingSet bs2 = new QueryBindingSet();
        bs2.addBinding("birthDate",new LiteralImpl("1983-04-18",new URIImpl("http://www.w3.org/2001/XMLSchema#date")));
        bs2.addBinding("x",new URIImpl("http://Bob"));

        final Set<BindingSet> bSets = Sets.newHashSet(bs,bs2);

        final CloseableIteration<BindingSet, QueryEvaluationException> results = ais.evaluate(bSets);

        final Set<BindingSet> fetchedResults = new HashSet<>();
        while(results.hasNext()) {
            final BindingSet next = results.next();
            fetchedResults.add(next);
        }

        Assert.assertEquals(Sets.<BindingSet>newHashSet(bs2), fetchedResults);
    }

    @Test
    public void accumuloIndexSetTestAttemptJoinAccrossTypes() throws Exception {
        // Load some Triples into Rya.
        final Set<Statement> triples = new HashSet<>();
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://hasAge"), new NumericLiteralImpl(14, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Alice"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://hasAge"), new NumericLiteralImpl(16, XMLSchema.INTEGER)) );
        triples.add( new StatementImpl(new URIImpl("http://Bob"), new URIImpl("http://playsSport"), new LiteralImpl("Soccer")) );

        for(final Statement triple : triples) {
            ryaConn.add(triple);
        }

        // Create a PCJ table will include those triples in its results.
        final String sparql =
                "SELECT ?name ?age " +
                "{" +
                  "?name <http://hasAge> ?age." +
                  "?name <http://playsSport> \"Soccer\" " +
                "}";

        final String pcjTableName = new PcjTableNameFactory().makeTableName(prefix, "testPcj");

        // Create and populate the PCJ table.
        PcjIntegrationTestingUtil.createAndPopulatePcj(ryaConn, accumuloConn, pcjTableName, sparql, new String[]{"name", "age"}, Optional.absent());
        final AccumuloIndexSet ais = new AccumuloIndexSet(conf,pcjTableName);

        final QueryBindingSet bs1 = new QueryBindingSet();
        bs1.addBinding("age",new LiteralImpl("16"));
        final QueryBindingSet bs2 = new QueryBindingSet();
        bs2.addBinding("age",new NumericLiteralImpl(14, XMLSchema.INTEGER));

        final Set<BindingSet> bSets = Sets.newHashSet(bs1,bs2);

        final CloseableIteration<BindingSet, QueryEvaluationException> results = ais.evaluate(bSets);

        final Set<BindingSet> fetchedResults = new HashSet<>();
        while(results.hasNext()) {
            final BindingSet next = results.next();
            fetchedResults.add(next);
        }

        bs2.addBinding("name", new URIImpl("http://Alice"));
        Assert.assertEquals(Sets.<BindingSet>newHashSet(bs2), fetchedResults);
    }

    @After
    public void close() throws RepositoryException {
        ryaConn.close();
        ryaRepo.shutDown();
    }

    private static Configuration getConf() {
        final AccumuloRdfConfiguration conf = new AccumuloRdfConfiguration();
        conf.setBoolean(ConfigUtils.USE_MOCK_INSTANCE, true);
        conf.set(RdfTripleStoreConfiguration.CONF_TBL_PREFIX, "rya_");
        conf.set(ConfigUtils.CLOUDBASE_USER, "root");
        conf.set(ConfigUtils.CLOUDBASE_PASSWORD, "");
        conf.set(ConfigUtils.CLOUDBASE_INSTANCE, "instance");
        conf.set(ConfigUtils.CLOUDBASE_AUTHS, "");
        return conf;
    }



}
