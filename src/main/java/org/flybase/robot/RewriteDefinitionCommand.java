/*
 * FlyBase ROBOT Plugin
 * Copyright © 2023 Damien Goutte-Gattat
 * 
 * This file is part of the FlyBase ROBOT Plugin project and distributed
 * under the terms of the MIT license. See the LICENSE.md file in that
 * project for the detailed conditions.
 */

package org.flybase.robot;

import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.obolibrary.robot.Command;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;
import org.obolibrary.robot.IOHelper;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;

/**
 * A ROBOT command to automatically rewrite definitions.
 * <p>
 * This command supports re-writing two types of definitions:
 * <ul>
 * <li>DOT-definitions, comprising of a single dot ("."): they are replaced by a
 * definition automatically generated from the logical definition of the class;
 * <li>SUB-definitions, of the form {@code $sub_PFX:1234}: they are replaced by
 * the definition of the term PFX:1234.
 * </ul>
 */
public class RewriteDefinitionCommand implements Command {

    private Options options;

    public RewriteDefinitionCommand() {
        options = CommandLineHelper.getCommonOptions();
        options.addOption("i", "input", true, "load ontology from file");
        options.addOption("I", "input-iri", true, "load ontology from IRI");
        options.addOption("o", "output", true, "save ontology to file");

        options.addOption("f", "filter-prefix", true, "only rewrite definitions for terms in specified prefix");
        options.addOption(null, "include-obsolete", false, "do not ignore obsoleted terms");

        options.addOption("d", "dot-definitions", false, "rewrite DOT definitions");
        options.addOption("D", "null-definitions", false, "treat null definitions as DOT definitions");
        options.addOption(null, "no-ids", false, "do not insert term IDs within generated definitions");
        options.addOption(null, "add-annotation", true, "add specified annotation to newly generated definitions");

        options.addOption("s", "sub-definitions", false, "rewrite SUB definitions");

        options.addOption(null, "write-to", true, "write new axioms to specified file");
    }

    @Override
    public String getName() {
        return "rewrite-def";
    }

    @Override
    public String getDescription() {
        return "Rewrite definitions";
    }

    @Override
    public String getUsage() {
        return "rewrite-def -i <FILE> [-f PFX] [-d] [-s] -o <FILE>";
    }

    @Override
    public Options getOptions() {
        return options;
    }

    @Override
    public void main(String[] args) {
        try {
            execute(null, args);
        } catch ( Exception e ) {
            CommandLineHelper.handleException(e);
        }
    }

    @Override
    public CommandState execute(CommandState state, String[] args) throws Exception {
        CommandLine line = CommandLineHelper.getCommandLine(getUsage(), options, args);
        if ( line == null ) {
            return null;
        }

        IOHelper ioHelper = CommandLineHelper.getIOHelper(line);
        state = CommandLineHelper.updateInputOntology(ioHelper, state, line);
        OWLOntology ontology = state.getOntology();

        BatchAnnotationRewriter rewriter = new BatchAnnotationRewriter();
        if ( line.hasOption('d') ) {
            DotDefinitionRewriter dotRewriter = new DotDefinitionRewriter(ontology, !line.hasOption("no-ids"));
            if ( line.hasOption("add-annotation") ) {
                for ( String value : line.getOptionValues("add-annotation") ) {
                    String[] parts = value.split(" ", 2);
                    if ( parts.length != 2 ) {
                        throw new Exception("Invalid value for --add-annotation");
                    }
                    dotRewriter.addDefaultAnnotation(ioHelper.createIRI(parts[0]), parts[1]);
                }
            }

            rewriter.addRewriter(dotRewriter);
        }
        if ( line.hasOption('s') ) {
            rewriter.addRewriter(new SubDefinitionRewriter(ontology));
        }
        if ( line.hasOption('f') ) {
            rewriter.setIRIFilter(Constants.OBO_PREFIX + line.getOptionValue('f'));
        }
        if ( line.hasOption('D') ) {
            rewriter.setGenerateIfNull(true);
        }
        if ( line.hasOption("include-obsolete") ) {
            rewriter.setRewriteForObsoleteTerms(true);
        }

        List<OWLOntologyChange> changes = rewriter.rewrite(ontology, Constants.DEFINITION_PROPERTY);

        // Optional save only the new axioms to a separate file
        if ( !changes.isEmpty() && line.hasOption("write-to") ) {
            OWLOntologyManager mgr = ontology.getOWLOntologyManager();
            OWLOntology output = mgr.createOntology();
            for ( OWLOntologyChange change : changes ) {
                if ( change.isAddAxiom() ) {
                    mgr.addAxiom(output, change.getAxiom());
                }
            }
            ioHelper.saveOntology(output, line.getOptionValue("write-to"));
        }

        ontology.getOWLOntologyManager().applyChanges(changes);
        CommandLineHelper.maybeSaveOutput(line, ontology);

        return state;
    }
}
