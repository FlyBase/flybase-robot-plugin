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
import java.util.HashSet;
import java.util.List;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.model.parameters.Navigation;

/**
 * Helper class to rewrite class annotations.
 */
public class BatchAnnotationRewriter {

    private HashSet<OWLAxiom> oldAxioms = new HashSet<OWLAxiom>();
    private HashSet<OWLAxiom> newAxioms = new HashSet<OWLAxiom>();
    private ArrayList<IAnnotationRewriter> rewriters = new ArrayList<IAnnotationRewriter>();
    private String iriFilter = null;
    private boolean generate = false;

    /**
     * Adds a rewriter to apply on annotation axioms. All rewriters added here will
     * be applied sequentially to each axiom selected for rewriting.
     * 
     * @param rewriter The annotation rewriter to add.
     */
    public void addRewriter(IAnnotationRewriter rewriter) {
        rewriters.add(rewriter);
    }

    /**
     * Sets a IRI prefix filter. By default, the rewriter will attempt to rewrite
     * axioms on all classes in the ontology (imports included). Sets this filter to
     * only rewrite axioms on classes whose IRI starts with a given prefix.
     * 
     * @param filter The IRI prefix to filter for (@code null to disable filtering).
     */
    public void setIRIFilter(String filter) {
        iriFilter = filter;
    }

    /**
     * Enables or disables the attempted production of a <em>de novo</em> annotation
     * if a class does not already have one.
     * 
     * @param generate {@code true} to enable, {@code false} to disable. It is
     *                 disabled by default.
     */
    public void setGenerateIfNull(boolean generate) {
        this.generate = generate;
    }

    /**
     * Rewrite class annotation axioms with the specified property.
     * 
     * @param ontology The ontology whose axioms should be rewritten.
     * @param property The annotation property to select.
     * @return The list of changes to apply to the ontology.
     */
    public List<OWLOntologyChange> rewrite(OWLOntology ontology, OWLAnnotationProperty property) {
        oldAxioms.clear();
        newAxioms.clear();

        for ( OWLClass c : ontology.getClassesInSignature(Imports.INCLUDED) ) {
            if ( iriFilter == null || c.getIRI().toString().startsWith(iriFilter) ) {
                rewrite(ontology, property, c);
            }
        }

        ArrayList<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
        oldAxioms.forEach(oldAxiom -> changes.add(new RemoveAxiom(ontology, oldAxiom)));
        newAxioms.forEach(newAxiom -> changes.add(new AddAxiom(ontology, newAxiom)));
        return changes;
    }

    /*
     * Rewrite annotations for a single class.
     */
    private void rewrite(OWLOntology ontology, OWLAnnotationProperty property, OWLClass c) {
        HashSet<OWLAnnotationAssertionAxiom> origAxioms = new HashSet<OWLAnnotationAssertionAxiom>();
        boolean isObsolete = false;

        for ( OWLAnnotationAssertionAxiom axiom : ontology.getAxioms(OWLAnnotationAssertionAxiom.class, c.getIRI(),
                Imports.INCLUDED, Navigation.IN_SUB_POSITION) ) {
            if ( axiom.getProperty().isDeprecated() ) {
                isObsolete = true;
            } else if ( axiom.getProperty().equals(property) ) {
                origAxioms.add(axiom);
            }
        }

        if ( isObsolete ) {
            return;
        }

        for ( OWLAnnotationAssertionAxiom origAxiom : origAxioms ) {
            for ( IAnnotationRewriter rewriter : rewriters ) {
                OWLAnnotationAssertionAxiom newAxiom = rewriter.rewrite(c, origAxiom);
                if ( newAxiom != null && newAxiom != origAxiom ) {
                    newAxioms.add(newAxiom);
                    oldAxioms.add(origAxiom);
                    break;
                }
            }
        }

        if ( origAxioms.isEmpty() && generate ) {
            for ( IAnnotationRewriter generator : rewriters ) {
                OWLAnnotationAssertionAxiom newAxiom = generator.rewrite(c);
                if ( newAxiom != null ) {
                    newAxioms.add(newAxiom);
                    break;
                }
            }
        }
    }
}
