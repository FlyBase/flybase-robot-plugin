/*
 * FlyBase ROBOT Plugin
 * Copyright © 2023 Damien Goutte-Gattat
 * 
 * This file is part of the FlyBase ROBOT Plugin project and distributed
 * under the terms of the MIT license. See the LICENSE.md file in that
 * project for the detailed conditions.
 */

package org.flybase.robot;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;

/**
 * This class is intended to hold a handful of constants that are used elsewhere
 * in this package.
 */
public class Constants {
    public static final String OBO_PREFIX = "http://purl.obolibrary.org/obo/";
    public static final String OIO_PREFIX = "http://www.geneontology.org/formats/oboInOwl#";
    public static final String FBGN_PREFIX = "http://flybase.org/reports/FBgn";

    public static final String DEFINITION = OBO_PREFIX + "IAO_0000115";
    public static final String HASDBXREF = OIO_PREFIX + "hasDbXref";
    public static final String OBOID = OIO_PREFIX + "id";

    public static final IRI DEFINITION_IRI = IRI.create(DEFINITION);
    public static final IRI HASDBXREF_IRI = IRI.create(HASDBXREF);
    public static final IRI OBOID_IRI = IRI.create(OBOID);

    public static final OWLAnnotationProperty DEFINITION_PROPERTY = OWLManager.getOWLDataFactory()
            .getOWLAnnotationProperty(DEFINITION_IRI);
    public static final OWLAnnotationProperty HASDBXREF_PROPERTY = OWLManager.getOWLDataFactory()
            .getOWLAnnotationProperty(HASDBXREF_IRI);
    public static final OWLAnnotationProperty OBOID_PROPERTY = OWLManager.getOWLDataFactory()
            .getOWLAnnotationProperty(OBOID_IRI);
}
