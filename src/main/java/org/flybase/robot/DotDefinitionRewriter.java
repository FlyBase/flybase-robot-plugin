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
import java.util.HashMap;
import java.util.List;

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

    private static final HashMap<String, String> propLabels = new HashMap<String, String>();

    // Override the default label of some properties to make the generated
    // definitions more readable
    static {
        propLabels.put(Constants.OBO_PREFIX + "BFO_0000050", "is part of");
        propLabels.put(Constants.OBO_PREFIX + "RO_0002100", "has its soma located in");
        propLabels.put(Constants.OBO_PREFIX + "RO_0002103", "electrically synapses to");
        propLabels.put(Constants.OBO_PREFIX + "RO_0002105", "is synapsed via type Ib bouton to");
        propLabels.put(Constants.OBO_PREFIX + "RO_0002106", "is synapsed via type Is bouton to");
        propLabels.put(Constants.OBO_PREFIX + "RO_0002107", "is synapsed via type II bouton to");
        propLabels.put(Constants.OBO_PREFIX + "RO_0002114", "is synapsed via type III bouton to");
        propLabels.put(Constants.OBO_PREFIX + "RO_0002150", "is connected to");
        propLabels.put(Constants.OBO_PREFIX + "RO_0002215", "is capable of");
        propLabels.put(Constants.OBO_PREFIX + "RO_0013009", "sends synaptic output to");
    }

    private OWLOntology ontology;
    private OWLDataFactory factory;

    /**
     * Creates a new instance.
     * 
     * @param ontology The ontology the axioms to rewrite belong to.
     */
    public DotDefinitionRewriter(OWLOntology ontology) {
        this.ontology = ontology;
        factory = ontology.getOWLOntologyManager().getOWLDataFactory();
    }

    @Override
    public OWLAnnotationAssertionAxiom rewrite(OWLClass c, OWLAnnotationAssertionAxiom original) {
        if ( !original.getValue().isLiteral() || !original.getValue().asLiteral().get().getLiteral().equals(".") ) {
            return original;
        }

        OWLAnnotationAssertionAxiom newAxiom = null;
        OWLClassExpression oce = getDefiningClassExpression(c);
        if ( oce != null ) {
            logger.debug(String.format("Class expression for %s: %s", c.getIRI().toQuotedString(), oce));

            DefinitionWriterVisitor visitor = new DefinitionWriterVisitor();
            oce.accept(visitor);
            String definition = visitor.getDefinition();

            newAxiom = factory.getOWLAnnotationAssertionAxiom(Constants.DEFINITION_PROPERTY, c.getIRI(),
                    factory.getOWLLiteral(definition), original.getAnnotations());
        } else {
            logger.debug(String.format("No class expression for %s", c.getIRI().toQuotedString()));
        }

        return newAxiom;
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
     * Get the label of the property. We first try to look up a human-readable
     * version, then fallback to the real label.
     */
    private String getLabel(OWLObjectProperty property) {
        String label = propLabels.get(property.getIRI().toString());
        if ( label == null ) {
            label = getLabel(property, false);
        }
        return label;
    }

    /*
     * A visitor to walk through a class expression and turn it into a
     * human-readable string.
     */
    private class DefinitionWriterVisitor extends OWLClassExpressionVisitorAdapter {

        ArrayList<String> items = new ArrayList<String>();

        public String getDefinition() {
            return "Any " + String.join(" ", items) + ".";
        }

        @Override
        public void visit(OWLObjectIntersectionOf ce) {
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
            for ( OWLObjectProperty prop : ce.getObjectPropertiesInSignature() ) {
                items.add(getLabel(prop));
            }
            OWLClassExpression filler = ce.getFiller();
            if ( !isFlyBaseGene(filler) ) {
                items.add("some");
            }
            ce.getFiller().accept(this);
        }

        @Override
        public void visit(OWLClass ce) {
            items.add(getLabel(ce, true));
        }

        private boolean isFlyBaseGene(OWLClassExpression ce) {
            return ce.isNamed() && ce.asOWLClass().getIRI().toString().startsWith(Constants.FBGN_PREFIX);
        }
    }
}
