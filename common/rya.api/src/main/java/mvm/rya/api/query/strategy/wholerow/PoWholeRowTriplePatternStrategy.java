package mvm.rya.api.query.strategy.wholerow;

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



import com.google.common.primitives.Bytes;
import mvm.rya.api.RdfCloudTripleStoreConfiguration;
import mvm.rya.api.RdfCloudTripleStoreConstants;
import mvm.rya.api.RdfCloudTripleStoreUtils;
import mvm.rya.api.domain.RyaRange;
import mvm.rya.api.domain.RyaType;
import mvm.rya.api.domain.RyaURI;
import mvm.rya.api.query.strategy.AbstractTriplePatternStrategy;
import mvm.rya.api.query.strategy.ByteRange;
import mvm.rya.api.resolver.RyaContext;
import mvm.rya.api.resolver.RyaTypeResolverException;

import java.io.IOException;
import java.util.Map;

import static mvm.rya.api.RdfCloudTripleStoreConstants.*;

/**
 * Date: 7/14/12
 * Time: 7:35 AM
 */
public class PoWholeRowTriplePatternStrategy extends AbstractTriplePatternStrategy {

    @Override
    public RdfCloudTripleStoreConstants.TABLE_LAYOUT getLayout() {
        return RdfCloudTripleStoreConstants.TABLE_LAYOUT.PO;
    }

    @Override
    public Map.Entry<RdfCloudTripleStoreConstants.TABLE_LAYOUT,
            ByteRange> defineRange(RyaURI subject, RyaURI predicate, RyaType object,
                                   RyaURI context, RdfCloudTripleStoreConfiguration conf) throws IOException {
        try {
            //po(ng)
            //po_r(s)(ng)
            //p(ng)
            //p_r(o)(ng)
            //r(p)(ng)
            if (!handles(subject, predicate, object, context)) return null;

            RyaContext ryaContext = RyaContext.getInstance();

            RdfCloudTripleStoreConstants.TABLE_LAYOUT table_layout = RdfCloudTripleStoreConstants.TABLE_LAYOUT.PO;
            byte[] start, stop;
            if (object != null) {
                if (object instanceof RyaRange) {
                    //p_r(o)
                    RyaRange rv = (RyaRange) object;
                    rv = ryaContext.transformRange(rv);
                    byte[] objStartBytes = ryaContext.serializeType(rv.getStart())[0];
                    byte[] objEndBytes = ryaContext.serializeType(rv.getStop())[0];
                    byte[] predBytes = predicate.getData().getBytes();
                    start = Bytes.concat(predBytes, DELIM_BYTES, objStartBytes);
                    stop = Bytes.concat(predBytes, DELIM_BYTES, objEndBytes, DELIM_BYTES, LAST_BYTES);
                } else {
                    if (subject != null && subject instanceof RyaRange) {
                        //po_r(s)
                        RyaRange ru = (RyaRange) subject;
                        ru = ryaContext.transformRange(ru);
                        byte[] subjStartBytes = ru.getStart().getData().getBytes();
                        byte[] subjStopBytes = ru.getStop().getData().getBytes();
                        byte[] predBytes = predicate.getData().getBytes();
                        byte[] objBytes = ryaContext.serializeType(object)[0];
                        start = Bytes.concat(predBytes, DELIM_BYTES, objBytes, DELIM_BYTES, subjStartBytes);
                        stop = Bytes.concat(predBytes, DELIM_BYTES, objBytes, DELIM_BYTES, subjStopBytes, TYPE_DELIM_BYTES, LAST_BYTES);
                    } else {
                        //po
                        //TODO: There must be a better way than creating multiple byte[]
                        byte[] objBytes = ryaContext.serializeType(object)[0];
                        start = Bytes.concat(predicate.getData().getBytes(), DELIM_BYTES, objBytes, DELIM_BYTES);
                        stop = Bytes.concat(start, LAST_BYTES);
                    }
                }
            } else if (predicate instanceof RyaRange) {
                //r(p)
                RyaRange rv = (RyaRange) predicate;
                rv = ryaContext.transformRange(rv);
                start = rv.getStart().getData().getBytes();
                stop = Bytes.concat(rv.getStop().getData().getBytes(), DELIM_BYTES, LAST_BYTES);
            } else {
                //p
                start = Bytes.concat(predicate.getData().getBytes(), DELIM_BYTES);
                stop = Bytes.concat(start, LAST_BYTES);
            }
            return new RdfCloudTripleStoreUtils.CustomEntry<RdfCloudTripleStoreConstants.TABLE_LAYOUT,
                    ByteRange>(table_layout, new ByteRange(start, stop));
        } catch (RyaTypeResolverException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean handles(RyaURI subject, RyaURI predicate, RyaType object, RyaURI context) {
        //po(ng)
        //p_r(o)(ng)
        //po_r(s)(ng)
        //p(ng)
        //r(p)(ng)
        if (predicate == null) return false;
        if (subject != null && !(subject instanceof RyaRange)) return false;
        if (predicate instanceof RyaRange)
            return object == null && subject == null;
        return subject == null || object != null;
    }
}
