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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.Validate;
import org.apache.hadoop.conf.Configuration;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.reasoner.rulesys.Rule.Parser;
import org.apache.log4j.Logger;
import org.apache.rya.api.RdfCloudTripleStoreConfiguration;
import org.apache.rya.api.RdfCloudTripleStoreConfiguration;
import org.apache.rya.api.domain.RyaStatement;
import org.apache.rya.api.resolver.RdfToRyaConversions;
import org.apache.rya.indexing.GeoConstants;
import org.apache.rya.indexing.GeoConstants;
import org.apache.rya.indexing.accumulo.ConfigUtils;
import org.apache.rya.indexing.accumulo.ConfigUtils;
import org.apache.rya.jena.jenasesame.JenaSesame;
import org.apache.rya.mongodb.MockMongoFactory;
import org.apache.rya.mongodb.MongoConnectorFactory;
import org.apache.rya.mongodb.MongoDBRdfConfiguration;
import org.apache.rya.mongodb.MongoDBRdfConfiguration;
import org.apache.rya.mongodb.MongoDBRyaDAO;
import org.apache.rya.rdftriplestore.RdfCloudTripleStore;
import org.apache.rya.rdftriplestore.RdfCloudTripleStore;
import org.apache.rya.rdftriplestore.inference.InferenceEngineException;
import org.apache.rya.rdftriplestore.inference.InferenceEngineException;
import org.apache.rya.sail.config.RyaSailFactory;
import org.apache.rya.sail.config.RyaSailFactory;
import org.glassfish.grizzly.http.util.Charsets;
import org.openrdf.model.Namespace;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.QueryResultHandlerException;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResultHandler;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.query.Update;
import org.openrdf.query.UpdateExecutionException;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.repository.sail.SailRepositoryConnection;
import org.openrdf.sail.Sail;

import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

public class MongoRyaDirectExample {
    private static final Logger log = Logger.getLogger(MongoRyaDirectExample.class);

    //
    // Connection configuration parameters
    //

    private static final boolean PRINT_QUERIES = true;
    private static final String MONGO_DB = "rya";
    private static final String MONGO_COLL_PREFIX = "rya_";
    private static final boolean USE_MOCK = true;
    private static final boolean USE_INFER = true;
    private static final String MONGO_INSTANCE_URL = "localhost";
    private static final String MONGO_INSTANCE_PORT = "27017";

    public static void main(final String[] args) throws Exception {
        final Configuration conf = getConf();
        conf.setBoolean(ConfigUtils.DISPLAY_QUERY_PLAN, PRINT_QUERIES);

        SailRepository repository = null;
        SailRepositoryConnection conn = null;
        try {
            log.info("Connecting to Indexing Sail Repository.");
            final Sail sail = RyaSailFactory.getInstance(conf);
            repository = new SailRepository(sail);
            conn = repository.getConnection();

            final long start = System.currentTimeMillis();
            log.info("Running SPARQL Example: Add and Delete");
            testAddAndDelete(conn);
            testAddAndDeleteNoContext(conn);
            testAddNamespaces(conn);
//            testAddPointAndWithinSearch(conn);
            testAddAndFreeTextSearchWithPCJ(conn);
           //  to test out inference, set inference to true in the conf
            if (USE_INFER){
            	testInfer(conn, sail);
            	testPropertyChainInference(conn, sail);
            	testPropertyChainInferenceAltRepresentation(conn, sail);
            }

            log.info("Running Jena Sesame Reasoning with Rules Example");
            testJenaSesameReasoningWithRules(conn);

            log.info("TIME: " + (System.currentTimeMillis() - start) / 1000.);
        } finally {
            log.info("Shutting down");
            closeQuietly(conn);
            closeQuietly(repository);
            if (mock != null) {
                mock.shutdown();
            }
            MongoConnectorFactory.closeMongoClient();
        }
    }

//    private static void testAddPointAndWithinSearch(SailRepositoryConnection conn) throws Exception {
//
//        String update = "PREFIX geo: <http://www.opengis.net/ont/geosparql#>  "//
//                + "INSERT DATA { " //
//                + "  <urn:feature> a geo:Feature ; " //
//                + "    geo:hasGeometry [ " //
//                + "      a geo:Point ; " //
//                + "      geo:asWKT \"Point(-77.03524 38.889468)\"^^geo:wktLiteral "//
//                + "    ] . " //
//                + "}";
//
//        Update u = conn.prepareUpdate(QueryLanguage.SPARQL, update);
//        u.execute();
//
//        String queryString;
//        TupleQuery tupleQuery;
//        CountingResultHandler tupleHandler;
//
//        // ring containing point
//        queryString = "PREFIX geo: <http://www.opengis.net/ont/geosparql#>  "//
//                + "PREFIX geof: <http://www.opengis.net/def/function/geosparql/>  "//
//                + "SELECT ?feature ?point ?wkt " //
//                + "{" //
//                + "  ?feature a geo:Feature . "//
//                + "  ?feature geo:hasGeometry ?point . "//
//                + "  ?point a geo:Point . "//
//                + "  ?point geo:asWKT ?wkt . "//
//                + "  FILTER(geof:sfWithin(?wkt, \"POLYGON((-78 39, -77 39, -77 38, -78 38, -78 39))\"^^geo:wktLiteral)) " //
//                + "}";//
//        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
//
//        tupleHandler = new CountingResultHandler();
//        tupleQuery.evaluate(tupleHandler);
//        log.info("Result count : " + tupleHandler.getCount());
//        Validate.isTrue(tupleHandler.getCount() >= 1); // may see points from during previous runs
//
//        // ring outside point
//        queryString = "PREFIX geo: <http://www.opengis.net/ont/geosparql#>  "//
//                + "PREFIX geof: <http://www.opengis.net/def/function/geosparql/>  "//
//                + "SELECT ?feature ?point ?wkt " //
//                + "{" //
//                + "  ?feature a geo:Feature . "//
//                + "  ?feature geo:hasGeometry ?point . "//
//                + "  ?point a geo:Point . "//
//                + "  ?point geo:asWKT ?wkt . "//
//                + "  FILTER(geof:sfWithin(?wkt, \"POLYGON((-77 39, -76 39, -76 38, -77 38, -77 39))\"^^geo:wktLiteral)) " //
//                + "}";//
//        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
//
//        tupleHandler = new CountingResultHandler();
//        tupleQuery.evaluate(tupleHandler);
//        log.info("Result count : " + tupleHandler.getCount());
//        Validate.isTrue(tupleHandler.getCount() == 0);
//    }

    private static void closeQuietly(final SailRepository repository) {
        if (repository != null) {
            try {
                repository.shutDown();
            } catch (final RepositoryException e) {
                // quietly absorb this exception
            }
        }
    }

    private static void closeQuietly(final SailRepositoryConnection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (final RepositoryException e) {
                // quietly absorb this exception
            }
        }
    }

    private static void testAddAndFreeTextSearchWithPCJ(final SailRepositoryConnection conn) throws Exception {
        // add data to the repository using the SailRepository add methods
        final ValueFactory f = conn.getValueFactory();
        final URI person = f.createURI("http://example.org/ontology/Person");

        String uuid;

        uuid = "urn:people:alice";
        conn.add(f.createURI(uuid), RDF.TYPE, person);
        conn.add(f.createURI(uuid), RDFS.LABEL, f.createLiteral("Alice Palace Hose", f.createURI("xsd:string")));

        uuid = "urn:people:bobss";
        conn.add(f.createURI(uuid), RDF.TYPE, person);
        conn.add(f.createURI(uuid), RDFS.LABEL, f.createLiteral("Bob Snob Hose", "en"));

        String queryString;
        TupleQuery tupleQuery;
        CountingResultHandler tupleHandler;

        // ///////////// search for alice
        queryString = "PREFIX fts: <http://rdf.useekm.com/fts#>  "//
                + "SELECT ?person ?match ?e ?c ?l ?o " //
                + "{" //
                + "  ?person <http://www.w3.org/2000/01/rdf-schema#label> ?match . "//
                + "  FILTER(fts:text(?match, \"Palace\")) " //
                + "}";//
        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
        tupleHandler = new CountingResultHandler();
        tupleQuery.evaluate(tupleHandler);
        log.info("Result count : " + tupleHandler.getCount());
        Validate.isTrue(tupleHandler.getCount() == 1);


        // ///////////// search for alice and bob
        queryString = "PREFIX fts: <http://rdf.useekm.com/fts#>  "//
                + "SELECT ?person ?match " //
                + "{" //
                + "  ?person <http://www.w3.org/2000/01/rdf-schema#label> ?match . "//
                  + "  ?person a <http://example.org/ontology/Person> . "//
                + "  FILTER(fts:text(?match, \"alice\")) " //
                + "}";//
        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
        tupleHandler = new CountingResultHandler();
        tupleQuery.evaluate(tupleHandler);
        log.info("Result count : " + tupleHandler.getCount());
        Validate.isTrue(tupleHandler.getCount() == 1);

     // ///////////// search for alice and bob
        queryString = "PREFIX fts: <http://rdf.useekm.com/fts#>  "//
                + "SELECT ?person ?match " //
                + "{" //
                + "  ?person a <http://example.org/ontology/Person> . "//
                + "  ?person <http://www.w3.org/2000/01/rdf-schema#label> ?match . "//
                + "  FILTER(fts:text(?match, \"alice\")) " //
                + "  FILTER(fts:text(?match, \"palace\")) " //
                + "}";//
        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
        tupleHandler = new CountingResultHandler();
        tupleQuery.evaluate(tupleHandler);
        log.info("Result count : " + tupleHandler.getCount());
        Validate.isTrue(tupleHandler.getCount() == 1);


        // ///////////// search for bob
        queryString = "PREFIX fts: <http://rdf.useekm.com/fts#>  "//
                + "SELECT ?person ?match ?e ?c ?l ?o " //
                + "{" //
                + "  ?person a <http://example.org/ontology/Person> . "//
                + "  ?person <http://www.w3.org/2000/01/rdf-schema#label> ?match . "//
                // this is an or query in mongo, a and query in accumulo
                + "  FILTER(fts:text(?match, \"alice hose\")) " //
                + "}";//

        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
        tupleHandler = new CountingResultHandler();
        tupleQuery.evaluate(tupleHandler);
        log.info("Result count : " + tupleHandler.getCount());
        Validate.isTrue(tupleHandler.getCount() == 2);
    }

    private static MockMongoFactory mock = null;
    private static Configuration getConf() throws IOException {

        final MongoDBRdfConfiguration conf = new MongoDBRdfConfiguration();
        conf.set(ConfigUtils.USE_MONGO, "true");

        if (USE_MOCK) {
            mock = MockMongoFactory.newFactory();
            final MongoClient c = mock.newMongoClient();
            final ServerAddress address = c.getAddress();
            final String url = address.getHost();
            final String port = Integer.toString(address.getPort());
            c.close();
            conf.set(MongoDBRdfConfiguration.MONGO_INSTANCE, url);
            conf.set(MongoDBRdfConfiguration.MONGO_INSTANCE_PORT, port);
        } else {
            // User name and password must be filled in:
            conf.set(MongoDBRdfConfiguration.MONGO_USER, "fill this in");
            conf.set(MongoDBRdfConfiguration.MONGO_USER_PASSWORD, "fill this in");
            conf.set(MongoDBRdfConfiguration.MONGO_INSTANCE, MONGO_INSTANCE_URL);
            conf.set(MongoDBRdfConfiguration.MONGO_INSTANCE_PORT, MONGO_INSTANCE_PORT);
        }
        conf.set(MongoDBRdfConfiguration.MONGO_DB_NAME, MONGO_DB);
        conf.set(MongoDBRdfConfiguration.MONGO_COLLECTION_PREFIX, MONGO_COLL_PREFIX);
        conf.set(ConfigUtils.GEO_PREDICATES_LIST, "http://www.opengis.net/ont/geosparql#asWKT");
//        conf.set(ConfigUtils.USE_GEO, "true");
        conf.set(ConfigUtils.USE_FREETEXT, "true");
        conf.setTablePrefix(MONGO_COLL_PREFIX);
        conf.set(ConfigUtils.GEO_PREDICATES_LIST, GeoConstants.GEO_AS_WKT.stringValue());
        conf.set(ConfigUtils.FREETEXT_PREDICATES_LIST, RDFS.LABEL.stringValue());
        conf.set(ConfigUtils.FREETEXT_PREDICATES_LIST, RDFS.LABEL.stringValue());
        conf.set(RdfCloudTripleStoreConfiguration.CONF_INFER, Boolean.toString(USE_INFER));
        return conf;
    }


    public static void testAddAndDelete(final SailRepositoryConnection conn) throws MalformedQueryException, RepositoryException,
    UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException {

    	// Add data
    	String query = "INSERT DATA\n"//
    			+ "{ GRAPH <http://updated/test> {\n"//
    			+ "  <http://acme.com/people/Mike> " //
    			+ "       <http://acme.com/actions/likes> \"A new book\" ;\n"//
    			+ "       <http://acme.com/actions/likes> \"Avocados\" .\n" + "} }";

    	log.info("Performing Query");

    	Update update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
    	update.execute();

    	query = "select ?p ?o { GRAPH <http://updated/test> {<http://acme.com/people/Mike> ?p ?o . }}";
    	final CountingResultHandler resultHandler = new CountingResultHandler();
    	TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
    	tupleQuery.evaluate(resultHandler);
    	log.info("Result count : " + resultHandler.getCount());

    	Validate.isTrue(resultHandler.getCount() == 2);

    	resultHandler.resetCount();

    	// Delete Data
    	query = "DELETE DATA\n" //
    			+ "{ GRAPH <http://updated/test> {\n"
    			+ "  <http://acme.com/people/Mike> <http://acme.com/actions/likes> \"A new book\" ;\n"
    			+ "   <http://acme.com/actions/likes> \"Avocados\" .\n" + "}}";

    	update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
    	update.execute();

    	query = "select ?p ?o { GRAPH <http://updated/test> {<http://acme.com/people/Mike> ?p ?o . }}";
    	tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
    	tupleQuery.evaluate(resultHandler);
    	log.info("Result count : " + resultHandler.getCount());

    	Validate.isTrue(resultHandler.getCount() == 0);
    }


    public static void testPropertyChainInferenceAltRepresentation(final SailRepositoryConnection conn, final Sail sail) throws MalformedQueryException, RepositoryException,
    UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException, InferenceEngineException {

    	// Add data
    	String query = "INSERT DATA\n"//
    			+ "{ GRAPH <http://updated/test> {\n"//
    			+ "  <urn:jenGreatGranMother> <urn:Motherof> <urn:jenGranMother> . "
    			+ "  <urn:jenGranMother> <urn:isChildOf> <urn:jenGreatGranMother> . "
    			+ "  <urn:jenGranMother> <urn:Motherof> <urn:jenMother> . "
    			+ "  <urn:jenMother> <urn:isChildOf> <urn:jenGranMother> . "
    			+ " <urn:jenMother> <urn:Motherof> <urn:jen> . "
    			+ "  <urn:jen> <urn:isChildOf> <urn:jenMother> . "
    			+ " <urn:jen> <urn:Motherof> <urn:jenDaughter> .  }}";

    	log.info("Performing Query");

    	Update update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
    	update.execute();

    	query = "select ?p { GRAPH <http://updated/test> {?s <urn:Motherof>/<urn:Motherof> ?p}}";
    	CountingResultHandler resultHandler = new CountingResultHandler();
    	TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
    	tupleQuery.evaluate(resultHandler);
    	log.info("Result count : " + resultHandler.getCount());


    	// try adding a property chain and querying for it
    	query = "INSERT DATA\n"//
    			+ "{ GRAPH <http://updated/test> {\n"//
    			+ "  <urn:greatMother> owl:propertyChainAxiom <urn:12342>  . " +
    			" <urn:12342> <http://www.w3.org/1999/02/22-rdf-syntax-ns#first> _:node1atjakcvbx15023 . " +
    			" _:node1atjakcvbx15023 <http://www.w3.org/2002/07/owl#inverseOf> <urn:isChildOf> . " +
    			" <urn:12342> <http://www.w3.org/1999/02/22-rdf-syntax-ns#rest> _:node1atjakcvbx15123 . " +
    			" _:node1atjakcvbx15123 <http://www.w3.org/1999/02/22-rdf-syntax-ns#rest> <http://www.w3.org/1999/02/22-rdf-syntax-ns#nil> . " +
    			" _:node1atjakcvbx15123 <http://www.w3.org/1999/02/22-rdf-syntax-ns#first> <urn:MotherOf> .  }}";
    	update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
    	update.execute();
    	((RdfCloudTripleStore) sail).getInferenceEngine().refreshGraph();

    	resultHandler.resetCount();
    	query = "select ?x { GRAPH <http://updated/test> {<urn:jenGreatGranMother> <urn:greatMother> ?x}}";
    	resultHandler = new CountingResultHandler();
    	tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
    	tupleQuery.evaluate(resultHandler);
    	log.info("Result count : " + resultHandler.getCount());

    }

    public static void testPropertyChainInference(final SailRepositoryConnection conn, final Sail sail) throws MalformedQueryException, RepositoryException,
    UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException, InferenceEngineException {

    	// Add data
    	String query = "INSERT DATA\n"//
    			+ "{ GRAPH <http://updated/test> {\n"//
    			+ "  <urn:paulGreatGrandfather> <urn:father> <urn:paulGrandfather> . "
    			+ "  <urn:paulGrandfather> <urn:father> <urn:paulFather> . " +
    			" <urn:paulFather> <urn:father> <urn:paul> . " +
    			" <urn:paul> <urn:father> <urn:paulSon> .  }}";

    	log.info("Performing Query");

    	Update update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
    	update.execute();

    	query = "select ?p { GRAPH <http://updated/test> {<urn:paulGreatGrandfather> <urn:father>/<urn:father> ?p}}";
    	CountingResultHandler resultHandler = new CountingResultHandler();
    	TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
    	tupleQuery.evaluate(resultHandler);
    	log.info("Result count : " + resultHandler.getCount());


    	// try adding a property chain and querying for it
    	query = "INSERT DATA\n"//
    			+ "{ GRAPH <http://updated/test> {\n"//
    			+ "  <urn:greatGrandfather> owl:propertyChainAxiom <urn:1234>  . " +
    			" <urn:1234> <http://www.w3.org/2000/10/swap/list#length> 3 . " +
    			" <urn:1234> <http://www.w3.org/2000/10/swap/list#index> (0 <urn:father>) . " +
    			" <urn:1234> <http://www.w3.org/2000/10/swap/list#index> (1 <urn:father>) . " +
    			" <urn:1234> <http://www.w3.org/2000/10/swap/list#index> (2 <urn:father>) .  }}";
    	update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
    	update.execute();
    	query = "INSERT DATA\n"//
    			+ "{ GRAPH <http://updated/test> {\n"//
    			+ "  <urn:grandfather> owl:propertyChainAxiom <urn:12344>  . " +
    			" <urn:12344> <http://www.w3.org/2000/10/swap/list#length> 2 . " +
    			" <urn:12344> <http://www.w3.org/2000/10/swap/list#index> (0 <urn:father>) . " +
    			" <urn:12344> <http://www.w3.org/2000/10/swap/list#index> (1 <urn:father>) .  }}";
    	update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
    	update.execute();
    	((RdfCloudTripleStore) sail).getInferenceEngine().refreshGraph();

    	resultHandler.resetCount();
    	query = "select ?p { GRAPH <http://updated/test> {<urn:paulGreatGrandfather> <urn:greatGrandfather> ?p}}";
    	resultHandler = new CountingResultHandler();
    	tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
    	tupleQuery.evaluate(resultHandler);
    	log.info("Result count : " + resultHandler.getCount());

    	resultHandler.resetCount();
    	query = "select ?s ?p { GRAPH <http://updated/test> {?s <urn:grandfather> ?p}}";
    	resultHandler = new CountingResultHandler();
    	tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
    	tupleQuery.evaluate(resultHandler);
    	log.info("Result count : " + resultHandler.getCount());

    }

    public static void testInfer(final SailRepositoryConnection conn, final Sail sail) throws MalformedQueryException, RepositoryException,
    UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException, InferenceEngineException {

    	// Add data
    	String query = "INSERT DATA\n"//
    			+ "{ \n"//
    			+ " <http://acme.com/people/Mike> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <urn:type1>.  "
    			+ " <urn:type1> <http://www.w3.org/2000/01/rdf-schema#subClassOf> <urn:superclass>.  }";

    	log.info("Performing Query");

    	final Update update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
    	update.execute();

    	// refresh the graph for inferencing (otherwise there is a five minute wait)
    	((RdfCloudTripleStore) sail).getInferenceEngine().refreshGraph();

    	query = "select ?s { ?s <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <urn:superclass> . }";
    	final CountingResultHandler resultHandler = new CountingResultHandler();
    	final TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
    	tupleQuery.evaluate(resultHandler);
    	log.info("Result count : " + resultHandler.getCount());

    	Validate.isTrue(resultHandler.getCount() == 1);

    	resultHandler.resetCount();
    }
    public static void testAddNamespaces(final SailRepositoryConnection conn) throws MalformedQueryException, RepositoryException,
    UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException {

    	conn.setNamespace("rya", "http://rya.com");
    	final RepositoryResult<Namespace> results = conn.getNamespaces();
    	for (final Namespace space : results.asList()){
    		System.out.println(space.getName() + ", " + space.getPrefix());
    	}
      }

    public static void testAddAndDeleteNoContext(final SailRepositoryConnection conn) throws MalformedQueryException, RepositoryException,
    UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException {

    	// Add data
    	String query = "INSERT DATA\n"//
    			+ "{ \n"//
    			+ "  <http://acme.com/people/Mike> " //
    			+ "       <http://acme.com/actions/likes> \"A new book\" ;\n"//
    			+ "       <http://acme.com/actions/likes> \"Avocados\" .\n" + " }";

    	log.info("Performing Query");

    	Update update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
    	update.execute();

    	query = "select ?p ?o {<http://acme.com/people/Mike> ?p ?o . }";
    	final CountingResultHandler resultHandler = new CountingResultHandler();
    	TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
    	tupleQuery.evaluate(resultHandler);
    	log.info("Result count : " + resultHandler.getCount());

    	Validate.isTrue(resultHandler.getCount() == 2);

    	resultHandler.resetCount();

    	// Delete Data
    	query = "DELETE DATA\n" //
    			+ "{ \n"
    			+ "  <http://acme.com/people/Mike> <http://acme.com/actions/likes> \"A new book\" ;\n"
    			+ "   <http://acme.com/actions/likes> \"Avocados\" .\n" + "}";

    	update = conn.prepareUpdate(QueryLanguage.SPARQL, query);
    	update.execute();

    	query = "select ?p ?o { {<http://acme.com/people/Mike> ?p ?o . }}";
    	tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
    	tupleQuery.evaluate(resultHandler);
    	log.info("Result count : " + resultHandler.getCount());

    	Validate.isTrue(resultHandler.getCount() == 0);
    }

    private static void testJenaSesameReasoningWithRules(final SailRepositoryConnection conn) throws Exception {
        final Repository repo = conn.getRepository();
        RepositoryConnection addConnection = null;
        RepositoryConnection queryConnection = null;
        try {
            // Load some data.
            addConnection = repo.getConnection();

            final String namespace = "http://tutorialacademy.com/2015/jena#";
            final Statement stmt1 = RyaDirectExample.createStatement("John", "hasClass", "Math", namespace);
            final Statement stmt2 = RyaDirectExample.createStatement("Bill", "teaches", "Math", namespace);
            final Statement inferredStmt = RyaDirectExample.createStatement("Bill", "hasStudent", "John", namespace);

            addConnection.add(stmt1);
            addConnection.add(stmt2);
            addConnection.close();

            queryConnection = repo.getConnection();

            final Dataset dataset = JenaSesame.createDataset(queryConnection);

            final Model model = dataset.getDefaultModel();
            log.info(model.getNsPrefixMap());

            final String ruleSource =
                "@prefix : <" + namespace + "> .\r\n" +
                "\r\n" +
                "[ruleHasStudent: (?s :hasClass ?c) (?p :teaches ?c) -> (?p :hasStudent ?s)]";

            Reasoner reasoner = null;
            try (
                final InputStream in = IOUtils.toInputStream(ruleSource, Charsets.UTF8_CHARSET);
                final BufferedReader br = new BufferedReader(new InputStreamReader(in));
            ) {
                final Parser parser = Rule.rulesParserFromReader(br);
                reasoner = new GenericRuleReasoner(Rule.parseRules(parser));
            }

            final InfModel infModel = ModelFactory.createInfModel(reasoner, model);

            final StmtIterator iterator = infModel.listStatements();

            int count = 0;
            while (iterator.hasNext()) {
                final org.apache.jena.rdf.model.Statement stmt = iterator.nextStatement();

                final Resource subject = stmt.getSubject();
                final Property predicate = stmt.getPredicate();
                final RDFNode object = stmt.getObject();

                log.info(subject.toString() + " " + predicate.toString() + " " + object.toString());
                // TODO: Should inferred statements be added automatically?
                model.add(stmt);
                count++;
            }
            log.info("Result count : " + count);

            // Check that statements exist in MongoDBRyaDAO
            final SailRepository sailRepository = ((SailRepository)repo);
            final RdfCloudTripleStore rdfCloudTripleStore = ((RdfCloudTripleStore)sailRepository.getSail());
            final MongoDBRyaDAO ryaDao = (MongoDBRyaDAO) rdfCloudTripleStore.getRyaDAO();

            final RyaStatement ryaStmt1 = RdfToRyaConversions.convertStatement(stmt1);
            final RyaStatement ryaStmt2 = RdfToRyaConversions.convertStatement(stmt2);
            final RyaStatement inferredRyaStmt = RdfToRyaConversions.convertStatement(inferredStmt);

            Validate.isTrue(RyaDirectExample.containsStatement(ryaStmt1, ryaDao));
            Validate.isTrue(RyaDirectExample.containsStatement(ryaStmt2, ryaDao));
            Validate.isTrue(RyaDirectExample.containsStatement(inferredRyaStmt, ryaDao));
        } catch (final Exception e) {
            log.error("Encountered an exception while running reasoner.", e);
        } finally {
            if (addConnection != null) {
                addConnection.close();
            }
            if (queryConnection != null) {
                queryConnection.close();
            }
        }
    }

    private static class CountingResultHandler implements TupleQueryResultHandler {
        private int count = 0;

        public int getCount() {
            return count;
        }

        public void resetCount() {
            this.count = 0;
        }

        @Override
        public void startQueryResult(final List<String> arg0) throws TupleQueryResultHandlerException {
        }

        @Override
        public void handleSolution(final BindingSet arg0) throws TupleQueryResultHandlerException {
            count++;
            System.out.println(arg0);
        }

        @Override
        public void endQueryResult() throws TupleQueryResultHandlerException {
        }

        @Override
        public void handleBoolean(final boolean arg0) throws QueryResultHandlerException {
          // TODO Auto-generated method stub

        }

        @Override
        public void handleLinks(final List<String> arg0) throws QueryResultHandlerException {
          // TODO Auto-generated method stub

        }
    }
}
