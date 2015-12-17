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

import java.util.List;

import mvm.rya.api.RdfCloudTripleStoreConfiguration;
import mvm.rya.indexing.RyaSailFactory;
import mvm.rya.indexing.accumulo.ConfigUtils;
import mvm.rya.mongodb.MongoDBRdfConfiguration;

import org.apache.commons.lang.Validate;
import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;
import org.openrdf.model.Namespace;
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
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.repository.sail.SailRepositoryConnection;
import org.openrdf.sail.Sail;

public class MongoRyaDirectExample {
    private static final Logger log = Logger.getLogger(MongoRyaDirectExample.class);

    //
    // Connection configuration parameters
    //

    private static final boolean PRINT_QUERIES = true;
    private static final String MONGO_DB = "rya";
    private static final String MONGO_COLL_PREFIX = "rya_";

    public static void main(String[] args) throws Exception {
        Configuration conf = getConf();
        conf.setBoolean(ConfigUtils.DISPLAY_QUERY_PLAN, PRINT_QUERIES);
  
        SailRepository repository = null;
        SailRepositoryConnection conn = null;
        try {
            log.info("Connecting to Indexing Sail Repository.");
            Sail sail = RyaSailFactory.getInstance(conf);
            repository = new SailRepository(sail);
            repository.initialize();
            conn = repository.getConnection();

            long start = System.currentTimeMillis();
            log.info("Running SPARQL Example: Add and Delete");
            testAddAndDelete(conn);
            testAddAndDeleteNoContext(conn);
            testAddNamespaces(conn);
            testAddPointAndWithinSearch(conn);

            log.info("TIME: " + (System.currentTimeMillis() - start) / 1000.);
        } finally {
            log.info("Shutting down");
            closeQuietly(conn);
            closeQuietly(repository);
        }
    }

    private static void testAddPointAndWithinSearch(SailRepositoryConnection conn) throws Exception {

        String update = "PREFIX geo: <http://www.opengis.net/ont/geosparql#>  "//
                + "INSERT DATA { " //
                + "  <urn:feature> a geo:Feature ; " //
                + "    geo:hasGeometry [ " //
                + "      a geo:Point ; " //
                + "      geo:asWKT \"Point(-77.03524 38.889468)\"^^geo:wktLiteral "//
                + "    ] . " //
                + "}";

        Update u = conn.prepareUpdate(QueryLanguage.SPARQL, update);
        u.execute();

        String queryString;
        TupleQuery tupleQuery;
        CountingResultHandler tupleHandler;

        // ring containing point
        queryString = "PREFIX geo: <http://www.opengis.net/ont/geosparql#>  "//
                + "PREFIX geof: <http://www.opengis.net/def/function/geosparql/>  "//
                + "SELECT ?feature ?point ?wkt " //
                + "{" //
                + "  ?feature a geo:Feature . "//
                + "  ?feature geo:hasGeometry ?point . "//
                + "  ?point a geo:Point . "//
                + "  ?point geo:asWKT ?wkt . "//
                + "  FILTER(geof:sfWithin(?wkt, \"POLYGON((-78 39, -77 39, -77 38, -78 38, -78 39))\"^^geo:wktLiteral)) " //
                + "}";//
        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);

        tupleHandler = new CountingResultHandler();
        tupleQuery.evaluate(tupleHandler);
        log.info("Result count : " + tupleHandler.getCount());
        Validate.isTrue(tupleHandler.getCount() >= 1); // may see points from during previous runs

        // ring outside point
        queryString = "PREFIX geo: <http://www.opengis.net/ont/geosparql#>  "//
                + "PREFIX geof: <http://www.opengis.net/def/function/geosparql/>  "//
                + "SELECT ?feature ?point ?wkt " //
                + "{" //
                + "  ?feature a geo:Feature . "//
                + "  ?feature geo:hasGeometry ?point . "//
                + "  ?point a geo:Point . "//
                + "  ?point geo:asWKT ?wkt . "//
                + "  FILTER(geof:sfWithin(?wkt, \"POLYGON((-77 39, -76 39, -76 38, -77 38, -77 39))\"^^geo:wktLiteral)) " //
                + "}";//
        tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);

        tupleHandler = new CountingResultHandler();
        tupleQuery.evaluate(tupleHandler);
        log.info("Result count : " + tupleHandler.getCount());
        Validate.isTrue(tupleHandler.getCount() == 0);
    }

    private static void closeQuietly(SailRepository repository) {
        if (repository != null) {
            try {
                repository.shutDown();
            } catch (RepositoryException e) {
                // quietly absorb this exception
            }
        }
    }

    private static void closeQuietly(SailRepositoryConnection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (RepositoryException e) {
                // quietly absorb this exception
            }
        }
    }

    private static Configuration getConf() {

        Configuration conf = new Configuration();
        conf.set(ConfigUtils.USE_MONGO, "true");
        conf.set(MongoDBRdfConfiguration.USE_TEST_MONGO, "true");
        conf.set(MongoDBRdfConfiguration.MONGO_DB_NAME, MONGO_DB);
        conf.set(MongoDBRdfConfiguration.MONGO_COLLECTION_PREFIX, MONGO_COLL_PREFIX);
        conf.set(ConfigUtils.GEO_PREDICATES_LIST, "http://www.opengis.net/ont/geosparql#asWKT");
        conf.set(ConfigUtils.USE_GEO, "true");
        conf.set(RdfCloudTripleStoreConfiguration.CONF_TBL_PREFIX, MONGO_COLL_PREFIX);
        
        return conf;
    }



    public static void testAddAndDelete(SailRepositoryConnection conn) throws MalformedQueryException, RepositoryException,
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
         CountingResultHandler resultHandler = new CountingResultHandler();
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

    public static void testAddNamespaces(SailRepositoryConnection conn) throws MalformedQueryException, RepositoryException,
    UpdateExecutionException, QueryEvaluationException, TupleQueryResultHandlerException {

    	conn.setNamespace("rya", "http://rya.com");
    	RepositoryResult<Namespace> results = conn.getNamespaces();
    	for (Namespace space : results.asList()){
    		System.out.println(space.getName() + ", " + space.getPrefix());
    	}
      }

    public static void testAddAndDeleteNoContext(SailRepositoryConnection conn) throws MalformedQueryException, RepositoryException,
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
    	CountingResultHandler resultHandler = new CountingResultHandler();
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

    private static class CountingResultHandler implements TupleQueryResultHandler {
        private int count = 0;

        public int getCount() {
            return count;
        }

        public void resetCount() {
            this.count = 0;
        }

        @Override
        public void startQueryResult(List<String> arg0) throws TupleQueryResultHandlerException {
        }

        @Override
        public void handleSolution(BindingSet arg0) throws TupleQueryResultHandlerException {
            count++;
        }

        @Override
        public void endQueryResult() throws TupleQueryResultHandlerException {
        }

        @Override
        public void handleBoolean(boolean arg0) throws QueryResultHandlerException {
          // TODO Auto-generated method stub
          
        }

        @Override
        public void handleLinks(List<String> arg0) throws QueryResultHandlerException {
          // TODO Auto-generated method stub
          
        }
    }
}
