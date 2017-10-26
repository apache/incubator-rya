package org.apache.rya.reasoning;

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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.hadoop.io.WritableComparable;
import org.apache.rya.api.domain.RyaStatement;
import org.apache.rya.api.resolver.RyaToRdfConversions;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;

/**
 * Represents a fact used and/or generated by the reasoner.
 */
public class Fact implements WritableComparable<Fact>, Cloneable {
    private static final ValueFactory VF = SimpleValueFactory.getInstance();

    Statement triple;

    // If this is a derived fact:
    Derivation derivation;

    // Potentially useful for future derivations
    boolean useful = true;

    // An empty fact
    public static final Fact NONE = new Fact();

    private static final String SEP = "\u0000";

    /**
     * Default constructor, contains no information
     */
    public Fact() { }

    /**
     * A fact containing a triple and no generating rule.
     */
    public Fact(Statement stmt) {
        this.triple = stmt;
    }

    /**
     * A fact containing a triple and no generating rule.
     */
    public Fact(Resource s, IRI p, Value o) {
        this.triple = VF.createStatement(s, p, o);
    }

    /**
     * A fact which contains a triple and was generated using a
     * particular rule by a reasoner for a particular node.
     */
    public Fact(Resource s, IRI p, Value o, int iteration,
            OwlRule rule, Resource node) {
        this.triple = VF.createStatement(s, p, o);
        this.derivation = new Derivation(iteration, rule, node);
    }

    public Statement getTriple() {
        return triple;
    }

    public Resource getSubject() {
        if (triple == null) {
            return null;
        }
        else {
            return triple.getSubject();
        }
    }

    public IRI getPredicate() {
        if (triple == null) {
            return null;
        }
        else {
            return triple.getPredicate();
        }
    }

    public Value getObject() {
        if (triple == null) {
            return null;
        }
        else {
            return triple.getObject();
        }
    }

    /**
     * Get the derivation if it exists, or the empty derivation otherwise.
     */
    public Derivation getDerivation() {
        if (derivation == null) {
            return Derivation.NONE;
        }
        else {
            return derivation;
        }
    }

    public boolean isInference() {
        return derivation != null;
    }

    public boolean isUseful() {
        return useful;
    }

    public boolean isEmpty() {
        return triple == null;
    }

    /**
     * Assign a particular statement to this fact.
     */
    public void setTriple(Statement stmt) {
        triple = stmt;
    }

    /**
     * Assign a particular statement to this fact.
     */
    public void setTriple(RyaStatement rs) {
        setTriple(RyaToRdfConversions.convertStatement(rs));
    }

    /**
     * Set a flag if this triple *could* be used in future derivations
     * (may only actually happen if certain other facts are seen as well.)
     */
    public void setUseful(boolean useful) {
        this.useful = useful;
    }

    /**
     * Set derivation. Allows reconstructing a fact and the way it was produced.
     */
    public void setDerivation(Derivation d) {
        this.derivation = d;
    }

    /**
     * Set derivation to null and return its former value. Allows decoupling
     * of the fact from the way it was produced.
     */
    public Derivation unsetDerivation() {
        Derivation d = getDerivation();
        this.derivation = null;
        return d;
    }

    /**
     * Generate a String showing this fact's derivation.
     * @param   multiline    Print a multi-line tree as opposed to a nested list
     * @param   schema       Use schema knowledge to further explain BNodes
     */
    public String explain(boolean multiline, Schema schema) {
        return explain(multiline, "", schema);
    }

    /**
     * Generate a String showing this fact's derivation. Does not incorporate
     * schema information.
     * @param   multiline    Print a multi-line tree as opposed to a nested list
     */
    public String explain(boolean multiline) {
        return explain(multiline, "", null);
    }

    /**
     * Recursively generate a String to show this fact's derivation.
     */
    String explain(boolean multiline, String prefix, Schema schema) {
        StringBuilder sb = new StringBuilder();
        String sep = " ";
        if (multiline) {
            sep = "\n" + prefix;
        }
        if (triple == null) {
            sb.append("(empty)").append(sep);
        }
        else {
            Resource s = getSubject();
            IRI p = getPredicate();
            Value o = getObject();
            sb.append("<").append(s.toString()).append(">").append(sep);
            sb.append("<").append(p.toString()).append(">").append(sep);
            sb.append("<").append(o.toString()).append(">");
            // Restrictions warrant further explanation
            if (schema != null && p.equals(RDF.TYPE)) {
                Resource objClass = (Resource) o;
                if (schema.hasRestriction(objClass)) {
                    sb.append(" { ");
                    sb.append(schema.explainRestriction(objClass));
                    sb.append(" }");
                }
            }
            sb.append(sep);
        }
        if (isInference()) {
            sb.append(derivation.explain(multiline, prefix, schema));
        }
        else {
            sb.append("[input]");
        }
        return sb.toString();
    }

    /**
     * Represent the content only, not the derivation.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (triple != null) {
            sb.append("<").append(getSubject().toString()).append("> ");
            sb.append("<").append(getPredicate().toString()).append("> ");
            if (getObject() instanceof Literal) {
                sb.append(getObject().toString());
            }
            else {
                sb.append("<").append(getObject().toString()).append(">");
            }
        }
        return sb.append(" .").toString();
    }

    @Override
    public void write(DataOutput out) throws IOException {
        if (triple == null) {
            out.writeInt(0);
        }
        else {
            StringBuilder sb = new StringBuilder();
            if (triple.getContext() != null) {
                sb.append(triple.getContext().toString());
            }
            sb.append(SEP).append(getSubject().toString());
            sb.append(SEP).append(getPredicate().toString());
            sb.append(SEP).append(getObject().toString());
            byte[] encoded = sb.toString().getBytes(StandardCharsets.UTF_8);
            out.writeInt(encoded.length);
            out.write(encoded);
        }
        out.writeBoolean(useful);
        // Write the derivation if there is one
        boolean derived = isInference();
        out.writeBoolean(derived);
        if (derived) {
            derivation.write(out);
        }
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        derivation = null;
        int tripleLength = in.readInt();
        if (tripleLength == 0) {
            triple = null;
        }
        else {
            byte[] tripleBytes = new byte[tripleLength];
            in.readFully(tripleBytes);
            String tripleString = new String(tripleBytes, StandardCharsets.UTF_8);
            String[] parts = tripleString.split(SEP);
            ValueFactory factory = SimpleValueFactory.getInstance();
            String context = parts[0];
            Resource s = null;
            IRI p = factory.createIRI(parts[2]);
            Value o = null;
            // Subject: either bnode or URI
            if (parts[1].startsWith("_")) {
                s = factory.createBNode(parts[1].substring(2));
            }
            else {
                s = factory.createIRI(parts[1]);
            }
            // Object: literal, bnode, or URI
            if (parts[3].startsWith("_")) {
                o = factory.createBNode(parts[3].substring(2));
            }
            else if (parts[3].startsWith("\"")) {
                //literal: may have language or datatype
                int close = parts[3].lastIndexOf("\"");
                int length = parts[3].length();
                String label = parts[3].substring(1, close);
                if (close == length - 1) {
                    // Just a string enclosed in quotes
                    o = factory.createLiteral(label);
                }
                else {
                    String data = parts[3].substring(close + 1);
                    if (data.startsWith("@")) {
                        String lang = data.substring(1);
                        o = factory.createLiteral(label, lang);
                    }
                    else if (data.startsWith("^^<")) {
                        o = factory.createLiteral(label, factory.createIRI(
                            data.substring(3, data.length() - 1)));
                    }
                }
            }
            else {
                o = factory.createIRI(parts[3]);
            }
            // Create a statement with or without context
            if (context.isEmpty()) {
                triple = VF.createStatement(s, p, o);
            }
            else {
                triple = VF.createStatement(s, p, o, factory.createIRI(context));
            }
        }
        useful = in.readBoolean();
        if (in.readBoolean()) {
            derivation = new Derivation();
            derivation.readFields(in);
        }
    }

    /**
     * Defines an ordering based on equals.
     * Two ReasonerFacts belong together if they represent the same
     * triple (regardless of where it came from). If they both are empty
     * (represent no triple), compare their derivations instead.
     */
    @Override
    public int compareTo(Fact other) {
        if (this.equals(other)) {
            return 0;
        }
        else if (other == null) {
            return 1;
        }
        if (this.triple == null) {
            if (other.triple == null) {
                // The only case where Derivation matters
                return this.getDerivation().compareTo(other.getDerivation());
            }
            else {
                // triple > no triple
                return -1;
            }
        }
        else if (other.triple == null) {
            // triple > no triple
            return 1;
        }
        // Compare two triples, ignoring where the information came from
        int result = this.getSubject().toString().compareTo(
            other.getSubject().toString());
        if (result == 0) {
            result = this.getPredicate().toString().compareTo(
                other.getPredicate().toString());
            if (result == 0) {
                result = this.getObject().toString().compareTo(
                    other.getObject().toString());
            }
        }
        return result;
    }

    /**
     * Two ReasonerFacts are equivalent if they represent the same triple
     * (regardless of where it came from). If they don't contain triples,
     * compare their derivations.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        Fact other = (Fact) o;
        if (this.triple == null) {
            if (other.triple == null) {
                // Derivations only matter if both facts are empty
                return this.getDerivation().equals(other.getDerivation());
            }
            else {
                return false;
            }
        }
        else {
            return this.triple.equals(other.triple);
        }
    }

    /**
     * Two statements are the same as long as they represent the same triple.
     * Derivation matters if and only if there is no triple.
     */
    @Override
    public int hashCode() {
        if (triple == null) {
            return getDerivation().hashCode();
        }
        else {
            return triple.hashCode();
        }
    }

    @Override
    public Fact clone() {
        Fact other = new Fact();
        other.triple = this.triple;
        other.useful = this.useful;
        if (this.derivation != null) {
            other.derivation = this.derivation.clone();
        }
        return other;
    }

    /**
     * Specify a source. Wrapper for Derivation.addSource. Instantiates a
     * derivation if none exists.
     */
    public void addSource(Fact other) {
        if (derivation == null) {
            derivation = new Derivation();
        }
        derivation.addSource(other);
    }

    /**
     * If this is a derived fact, get the iteration it was derived, otherwise
     * return zero.
     */
    public int getIteration() {
        if (derivation == null) {
            return 0;
        }
        else {
            return derivation.getIteration();
        }
    }

    /**
     * Return whether this fact has itself as a source.
     */
    public boolean isCycle() {
        return derivation != null && derivation.hasSource(this);
    }

    /**
     * Return whether a particular fact is identical to one used to derive this.
     * Wrapper for Derivation.hasSource.
     */
    public boolean hasSource(Fact other) {
        return derivation != null && derivation.hasSource(other);
    }

    /**
     * Return whether this fact was derived using a particular rule.
     */
    public boolean hasRule(OwlRule rule) {
        return derivation != null && derivation.getRule() == rule;
    }

    /**
     * Get the size of the derivation tree, computed by counting up the number
     * of distinct nodes that are part of this edge or were used to produce this
     * fact, minus 1. An input triple has span 1, a triple derived in one reduce
     * step has span 2, etc. Related to the derivation's span, but takes
     * subject and object of this fact into account.
     */
    public int span() {
        if (isInference()) {
            int d = derivation.span() + 1;
            if (derivation.hasSourceNode(getSubject())) {
                d--;
            }
            if (derivation.hasSourceNode(getObject())) {
                d--;
            }
            return d;
        }
        else {
            return 1;
        }
    }
}
