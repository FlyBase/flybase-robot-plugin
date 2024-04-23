/*
 * FlyBase ROBOT Plugin
 * Copyright © 2023 Damien Goutte-Gattat
 * 
 * This file is part of the FlyBase ROBOT Plugin project and distributed
 * under the terms of the MIT license. See the LICENSE.md file in that
 * project for the detailed conditions.
 */

package org.flybase.robot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.ClassExpressionType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.model.parameters.Navigation;
import org.semanticweb.owlapi.util.OWLClassExpressionVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A rewriter to rewrite "DOT" definitions.
 * <p>
 * DOT definitions comprises of a single dot character ("."). This rewriter
 * replaces them with a fully auto-generated definition that is a human-readable
 * form of the logical definition of the class.
 */
public class DotDefinitionRewriter implements IAnnotationRewriter {

    private static final Logger logger = LoggerFactory.getLogger(DotDefinitionRewriter.class);

    private OWLOntology ontology;
    private OWLDataFactory factory;
    private Set<OWLAnnotation> defaultAnnotations = new HashSet<OWLAnnotation>();
    private boolean includeID;

    /**
     * Creates a new instance.
     * 
     * @param ontology The ontology the axioms to rewrite belong to.
     */
    public DotDefinitionRewriter(OWLOntology ontology) {
        this(ontology, true);
    }

    /**
     * Creates a new instance that optionally does not insert IDs within
     * definitions.
     * 
     * @param ontology The ontology the axioms to rewrite belong to.
     * @param withID   If {@code true} (default, term labels are followed by their
     *                 ID in the generated definitions.
     */
    public DotDefinitionRewriter(OWLOntology ontology, boolean withID) {
        this.ontology = ontology;
        factory = ontology.getOWLOntologyManager().getOWLDataFactory();
        includeID = withID;
    }

    @Override
    public OWLAnnotationAssertionAxiom rewrite(OWLClass c, OWLAnnotationAssertionAxiom original) {
        if ( !original.getValue().isLiteral() || !original.getValue().asLiteral().get().getLiteral().equals(".") ) {
            return original;
        }

        return generate(c, original.getAnnotations());
    }

    @Override
    public OWLAnnotationAssertionAxiom rewrite(OWLClass c) {
        return generate(c, defaultAnnotations);
    }

    /*
     * Common logic to both forms of rewrite.
     */
    private OWLAnnotationAssertionAxiom generate(OWLClass c, Set<OWLAnnotation> annotations) {
        OWLClassExpression oce = getDefiningClassExpression(c);
        if ( oce != null ) {
            logger.debug(String.format("Class expression for %s: %s", c.getIRI().toQuotedString(), oce));

            DefinitionWriterVisitor visitor = new DefinitionWriterVisitor();
            oce.accept(visitor);
            String definition = visitor.getDefinition();

            return factory.getOWLAnnotationAssertionAxiom(Constants.DEFINITION_PROPERTY, c.getIRI(),
                    factory.getOWLLiteral(definition), annotations);
        }

        logger.debug(String.format("No class expression for %s", c.getIRI().toQuotedString()));
        return null;
    }

    /*
     * Get the logical definition of the class.
     */
    private OWLClassExpression getDefiningClassExpression(OWLClass c) {
        for ( OWLAxiom ax : ontology.getAxioms(c, Imports.INCLUDED) ) {
            if ( ax instanceof OWLEquivalentClassesAxiom ) {
                OWLEquivalentClassesAxiom aca = (OWLEquivalentClassesAxiom) ax;
                for (OWLClassExpression oce : aca.getClassExpressionsAsList()) {
                    if ( !oce.getObjectPropertiesInSignature().isEmpty() ) {
                        return oce;
                    }
                }
            }
        }
        return null;
    }

    /*
     * Get the label of the entity, optionally followed by its short ID.
     */
    private String getLabel(OWLNamedObject entity, boolean withID) {
        String iri = entity.getIRI().toString();
        String label = null;
        String id = null;
        for ( OWLAnnotationAssertionAxiom axiom : ontology.getAxioms(OWLAnnotationAssertionAxiom.class, entity.getIRI(),
                Imports.INCLUDED, Navigation.IN_SUB_POSITION) ) {
            if ( axiom.getProperty().isLabel() && axiom.getValue().isLiteral() ) {
                label = axiom.getValue().asLiteral().get().getLiteral();
            } else if ( axiom.getProperty().equals(Constants.OBOID_PROPERTY)
                    && axiom.getValue().isLiteral() ) {
                id = axiom.getValue().asLiteral().get().getLiteral();
            }
        }

        // Gene entities don't have a OBO ID, so we fabricate one from the IRI
        if ( id == null && iri.startsWith(Constants.FBGN_PREFIX) ) {
            id = "FBgn" + iri.substring(Constants.FBGN_PREFIX.length());
        }

        if ( withID && label != null && id != null ) {
            return label + " (" + id + ")";
        } else if ( label != null ) {
            return label;
        } else if ( id != null ) {
            return id;
        } else {
            return iri;
        }
    }

    /*
     * A visitor to walk through a class expression and turn it into a
     * human-readable string.
     */
    private class DefinitionWriterVisitor extends OWLClassExpressionVisitorAdapter {

        ArrayList<String> items = new ArrayList<String>();
        ClassExpressionType currentType;

        public String getDefinition() {
            return String.join(" ", items) + ".";
        }

        @Override
        public void visit(OWLObjectIntersectionOf ce) {
            currentType = ce.getClassExpressionType();
            List<OWLClassExpression> operands = ce.getOperandsAsList();
            for ( int i = 0; i < operands.size(); i++ ) {
                operands.get(i).accept(this);
                if ( i == 0 ) {
                    items.add("that");
                } else if ( i < operands.size() - 1 ) {
                    items.add("and");
                }
            }
        }

        @Override
        public void visit(OWLObjectSomeValuesFrom ce) {
            currentType = ce.getClassExpressionType();
            for ( OWLObjectProperty prop : ce.getObjectPropertiesInSignature() ) {
                ObjectProperty op = ObjectProperty.fromIRI(prop.getIRI());
                if ( op != null ) {
                    items.add(op.getHumanExpression());
                } else {
                    // Use the property's own label and default connecting word
                    items.add(getLabel(prop, false));
                    items.add("some");
                }
            }
            ce.getFiller().accept(this);
        }

        @Override
        public void visit(OWLClass ce) {
            if ( currentType == ClassExpressionType.OBJECT_INTERSECTION_OF ) {
                if ( items.isEmpty() ) {
                    items.add("Any");
                } else {
                    items.add("is a(n)");
                }
            }
            items.add(getLabel(ce, includeID));
        }
    }
}

/**
 * Represents some object properties for which we override the label, in order
 * to build sentences that flow somewhat more nicely.
 */
enum ObjectProperty {
    // @formatter:off
    // PROP ID,  LABEL,                                     CONNECTING WORD
    BFO_0000050 ("is part of"                                 ),
    BFO_0000051 ("has part"                                   ),
    RO_0002100  ("has its soma located in"                    ),
    RO_0002103  ("electrically synapses to"                   ),
    RO_0002105  ("is synapsed via type Ib bouton to"          ),
    RO_0002106  ("is synapsed via type Is bouton to"          ),
    RO_0002107  ("is synapsed via type II bouton to"          ),
    RO_0002114  ("is synapsed via type III bouton to"         ),
    RO_0002150  ("is continuous with"                         ),
    RO_0002160  ("only exists in",                          ""),
    RO_0002170  ("is connected to"                            ),
    RO_0002215  ("is capable of"                              ),
    RO_0002216  ("is capable of part of"                      ),
    RO_0002292  ("expresses",                               ""),
    RO_0013009  ("sends synaptic output to"                   );
    // @formatter:on

    private final static Map<IRI, ObjectProperty> MAP;

    static {
        Map<IRI, ObjectProperty> map = new HashMap<IRI, ObjectProperty>();
        for ( ObjectProperty value : ObjectProperty.values() ) {
            map.put(IRI.create(Constants.OBO_PREFIX + value.name()), value);
        }

        MAP = Collections.unmodifiableMap(map);
    }

    private final String label;
    private final String connector;

    ObjectProperty(String label, String connector) {
        this.label = label;
        this.connector = connector;
    }

    ObjectProperty(String label) {
        this(label, null);
    }

    public String getHumanExpression() {
        if ( connector == null ) {
            return label + " some";
        } else if ( !connector.isEmpty() ) {
            return label + connector;
        } else {
            return label;
        }
    }

    public static ObjectProperty fromIRI(IRI iri) {
        return MAP.getOrDefault(iri, null);
    }
}
