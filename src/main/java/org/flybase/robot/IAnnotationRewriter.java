/*
 * FlyBase ROBOT Plugin
 * Copyright © 2023 Damien Goutte-Gattat
 * 
 * This file is part of the FlyBase ROBOT Plugin project and distributed
 * under the terms of the MIT license. See the LICENSE.md file in that
 * project for the detailed conditions.
 */

package org.flybase.robot;

import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClass;

/**
 * Represents an object that can arbitrarily rewrite an annotation assertion
 * value.
 */
public interface IAnnotationRewriter {

    /**
     * Rewrites an annotation value.
     * 
     * @param c        The class whose annotation should be rewritten.
     * @param original The annotation axiom with the original value.
     * @return An annotation axiom with the new value. If this rewriter is not
     *         suitable to rewrite the value for this particular class, it should
     *         return the original axiom unmodified. If the rewriter is suitable but
     *         fails to rewrite the value for any reason, it should return
     *         {@code null}.
     */
    public OWLAnnotationAssertionAxiom rewrite(OWLClass c, OWLAnnotationAssertionAxiom original);
}
