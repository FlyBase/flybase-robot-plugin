/*
 * FlyBase ROBOT Plugin
 * Copyright © 2023 Damien Goutte-Gattat
 * 
 * This file is part of the FlyBase ROBOT Plugin project and distributed
 * under the terms of the MIT license. See the LICENSE.md file in that
 * project for the detailed conditions.
 */

package org.flybase.robot;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.model.parameters.Navigation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A rewriter to rewrite "SUB" definitions.
 * <p>
 * SUB definitions are definitions containing a special string of the form
 * {@code $sub_PFX:1234}. This rewriter rewrites the definition by replacing
 * that special string with the definition carried by the entity referenced by
 * {@code PFX:1234}.
 */
public class SubDefinitionRewriter implements IAnnotationRewriter {

    private static final Logger logger = LoggerFactory.getLogger(SubDefinitionRewriter.class);

    private static final Pattern SUB_PATTERN = Pattern.compile("\\$sub[_]([a-zA-Z]+)[:]([0-9]+)");

    private OWLOntology ontology;
    private OWLDataFactory factory;

    public SubDefinitionRewriter(OWLOntology ontology) {
        this.ontology = ontology;
        factory = ontology.getOWLOntologyManager().getOWLDataFactory();
    }

    @Override
    public OWLAnnotationAssertionAxiom rewrite(OWLClass c, OWLAnnotationAssertionAxiom original) {
        // We only work on literal values
        if ( !original.getValue().isLiteral() ) {
            return original;
        }

        // Nothing to do if the definition does not contain a "$sub_PFX:1234" motif
        String originalDef = original.getValue().asLiteral().get().getLiteral();
        Matcher m = SUB_PATTERN.matcher(originalDef);
        if ( !m.find() ) {
            return original;
        }

        HashSet<OWLAnnotation> annots = new HashSet<OWLAnnotation>(original.getAnnotations());
        String foreignDef;

        // Find the external definition to copy
        OWLAnnotationAssertionAxiom foreignDefAxiom = getDefinition(
                IRI.create(Constants.OBO_PREFIX + m.group(1) + "_" + m.group(2)));
        if ( foreignDefAxiom != null ) {
            foreignDef = foreignDefAxiom.getValue().asLiteral().get().getLiteral();
            annots.addAll(foreignDefAxiom.getAnnotations());
        } else {
            // Create a pseudo-definition that at least refers to the foreign term
            foreignDef = "No definition for " + m.group(1) + ":" + m.group(2) + ".";
            logger.debug(String.format("No definition found for %s", c.getIRI().toQuotedString()));
        }

        // If the definition we copied is the entire new definition (i.e. the original
        // definition contained only the "$sub_PFX:1234" pattern), we append to suffix
        // so that the new definition differs from the one it was copied over (to avoid
        // "duplicate definition" errors).
        if ( m.start() == 0 && m.end() == originalDef.length() && foreignDefAxiom != null ) {
            foreignDef = foreignDef.substring(0, foreignDef.length() - 1) + " (from " + m.group(1) + ").";
        }

        return factory.getOWLAnnotationAssertionAxiom(Constants.DEFINITION_PROPERTY, c.getIRI(),
                factory.getOWLLiteral(m.replaceAll(foreignDef)), annots);
    }

    /*
     * Find the definition axiom for the given term.
     */
    private OWLAnnotationAssertionAxiom getDefinition(IRI iri) {
        for ( OWLAnnotationAssertionAxiom axiom : ontology.getAxioms(OWLAnnotationAssertionAxiom.class, iri,
                Imports.INCLUDED, Navigation.IN_SUB_POSITION) ) {
            if ( axiom.getProperty().equals(Constants.DEFINITION_PROPERTY) && axiom.getValue().isLiteral() ) {
                return axiom;
            }
        }
        return null;
    }
}
