package org.apache.rya.api.persist.query.join;

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

import com.google.common.base.Preconditions;
import org.apache.rya.api.RdfCloudTripleStoreConfiguration;
import org.apache.rya.api.domain.RyaIRI;
import org.apache.rya.api.domain.RyaIRIRange;
import org.apache.rya.api.domain.RyaRange;
import org.apache.rya.api.domain.RyaResource;
import org.apache.rya.api.domain.RyaStatement;
import org.apache.rya.api.domain.RyaValue;
import org.apache.rya.api.persist.RyaDAOException;
import org.apache.rya.api.persist.query.RyaQueryEngine;
import org.apache.rya.api.persist.utils.RyaDAOHelper;
import org.apache.rya.api.resolver.RyaContext;
import org.apache.rya.api.utils.PeekingCloseableIteration;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.EmptyIteration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Date: 7/24/12
 * Time: 8:52 AM
 */
public class MergeJoin<C extends RdfCloudTripleStoreConfiguration> implements Join<C> {

    private RyaContext ryaContext = RyaContext.getInstance();
    private RyaQueryEngine ryaQueryEngine;

    public MergeJoin() {
    }

    public MergeJoin(RyaQueryEngine ryaQueryEngine) {
        this.ryaQueryEngine = ryaQueryEngine;
    }

    /**
     * Return all statements that have input predicates. Predicates must not be null or ranges
     *
     * @param preds
     * @return
     */
    @Override
    public CloseableIteration<RyaStatement, RyaDAOException> join(C conf, RyaIRI... preds)
            throws RyaDAOException {
        Preconditions.checkNotNull(preds);
        Preconditions.checkArgument(preds.length > 1, "Must join 2 or more");
        // TODO: Reorder predObjs based on statistics
        final List<CloseableIteration<RyaStatement, RyaDAOException>> iters = new ArrayList<>();
        for (RyaIRI predicate : preds) {
            Preconditions.checkArgument(predicate != null && !(predicate instanceof RyaRange));

            CloseableIteration<RyaStatement, RyaDAOException> iter = RyaDAOHelper.query(ryaQueryEngine,
                    new RyaStatement(null, predicate, null), conf);
            iters.add(iter);
        }
        Preconditions.checkArgument(iters.size() > 1, "Must join 2 or more");

        final CloseableIteration<RyaStatement, RyaDAOException> first = iters.remove(0);

        //perform merge operation

        return new CloseableIteration<RyaStatement, RyaDAOException>() {

            private RyaStatement first_stmt;
            private RyaValue first_obj;

            @Override
            public void close() throws RyaDAOException {
                for (CloseableIteration<RyaStatement, RyaDAOException> iter : iters) {
                    iter.close();
                }
            }

            @Override
            public boolean hasNext() throws RyaDAOException {
                return first_stmt != null || check();
            }

            @Override
            public RyaStatement next() throws RyaDAOException {
                if (first_stmt != null) {
                    RyaStatement temp = first_stmt;
                    first_stmt = null;
                    return temp;
                }
                if (check()) {
                    RyaStatement temp = first_stmt;
                    first_stmt = null;
                    return temp;
                }
                return null;
            }

            @Override
            public void remove() throws RyaDAOException {
                this.next();
            }

            protected boolean check() throws RyaDAOException {
                if (!first.hasNext()) return false;
                first_stmt = first.next();
                first_obj = first_stmt.getObject();
                for (CloseableIteration<RyaStatement, RyaDAOException> iter : iters) {
                    if (!iter.hasNext()) return false; //no more left to join
                    RyaValue iter_obj = iter.next().getObject();
                    while (first_obj.stringValue().compareTo(iter_obj.stringValue()) < 0) {
                        if (!first.hasNext()) return false;
                        first_obj = first.next().getObject();
                    }
                    while (first_obj.stringValue().compareTo(iter_obj.stringValue()) > 0) {
                        if (!iter.hasNext()) return false;
                        iter_obj = iter.next().getObject();
                    }
                }
                return true;
            }
        };
    }

    /**
     * Return all subjects that have the predicate objects associated. Predicate and objects must be not null or ranges
     * to ensure sorting
     *
     * @param predObjs
     * @return
     * @throws RyaDAOException
     */
    @Override
    public CloseableIteration<RyaResource, RyaDAOException> join(C conf, Map.Entry<RyaIRI, RyaValue>... predObjs)
            throws RyaDAOException {
        Preconditions.checkNotNull(predObjs);
        Preconditions.checkArgument(predObjs.length > 1, "Must join 2 or more");

        // TODO: Reorder predObjs based on statistics
        final List<CloseableIteration<RyaStatement, RyaDAOException>> iters = new ArrayList<>();
        RyaResource earliest_subject = null;
        for (Map.Entry<RyaIRI, RyaValue> predObj : predObjs) {
            RyaIRI predicate = predObj.getKey();
            RyaValue object = predObj.getValue();
            Preconditions.checkArgument(predicate != null && !(predicate instanceof RyaRange));
            Preconditions.checkArgument(object != null && !(object instanceof RyaRange));

            PeekingCloseableIteration<RyaStatement, RyaDAOException> iter = null;
            if (earliest_subject == null) {
                iter = new PeekingCloseableIteration<>(
                        RyaDAOHelper.query(ryaQueryEngine, new RyaStatement(null, predicate, object), conf));
            } else {
                iter = new PeekingCloseableIteration<>(
                        RyaDAOHelper.query(ryaQueryEngine, new RyaStatement(new RyaIRIRange(earliest_subject, RyaIRIRange.LAST_IRI), predicate, object), conf));
            }
            if (!iter.hasNext()) {
                return new EmptyIteration<>();
            }
            //setting up range to make performant query
            earliest_subject = iter.peek().getSubject();
            iters.add(iter);
        }
        Preconditions.checkArgument(iters.size() > 1, "Must join 2 or more");

        final CloseableIteration<RyaStatement, RyaDAOException> first = iters.remove(0);

        //perform merge operation

        return new CloseableIteration<RyaResource, RyaDAOException>() {

            private RyaResource first_subj;

            @Override
            public void close() throws RyaDAOException {
                for (CloseableIteration<RyaStatement, RyaDAOException> iter : iters) {
                    iter.close();
                }
            }

            @Override
            public boolean hasNext() throws RyaDAOException {
                return first_subj != null || check();
            }

            @Override
            public RyaResource next() throws RyaDAOException {
                if (first_subj != null) {
                    RyaResource temp = first_subj;
                    first_subj = null;
                    return temp;
                }
                if (check()) {
                    RyaResource temp = first_subj;
                    first_subj = null;
                    return temp;
                }
                return null;
            }

            @Override
            public void remove() throws RyaDAOException {
                this.next();
            }

            protected boolean check() throws RyaDAOException {
                if (!first.hasNext()) return false;
                first_subj = first.next().getSubject();
                for (CloseableIteration<RyaStatement, RyaDAOException> iter : iters) {
                    if (!iter.hasNext()) return false; //no more left to join
                    RyaResource iter_subj = iter.next().getSubject();
                    while (first_subj.stringValue().compareTo(iter_subj.stringValue()) < 0) {
                        if (!first.hasNext()) return false;
                        first_subj = first.next().getSubject();
                    }
                    while (first_subj.stringValue().compareTo(iter_subj.stringValue()) > 0) {
                        if (!iter.hasNext()) return false;
                        iter_subj = iter.next().getSubject();
                    }
                }
                return true;
            }
        };
    }

    public RyaQueryEngine getRyaQueryEngine() {
        return ryaQueryEngine;
    }

    public void setRyaQueryEngine(RyaQueryEngine ryaQueryEngine) {
        this.ryaQueryEngine = ryaQueryEngine;
    }
}
