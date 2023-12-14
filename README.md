FlyBase ROBOT Plugin
====================

The FlyBase ROBOT plugin is a plugin for the
[ROBOT](http://robot.obolibrary.org/) ontology manipulation tool. It
provides additional commands that are used in FlyBase ontological
pipelines.

Setup
-----
Build with Maven by running:

```sh
mvn clean package
```

This will produce a `flybase.jar` file in the `target` directory. Place
this file in your ROBOT plugins directory (by default
`~/.robot/plugins`).

Provided commands
-----------------

### flybase:rewrite-def
This command is intended to replace the
[EQWriter](https://github.com/monarch-ebi-dev/eqwriter) tool. It
rewrites definitions that follow a given pattern.

Two types of rewriting are supported:

* rewriting of "DOT-definitions": definitions made of a single dot
  character (".") are replaced by an automatically generated definition
  that is a human-readable form of the logical definition of the class;
* rewriting of "SUB-definitions": definitions that are of the form
  "$sub_PFX:1234" are replaced by the definition of the PFX:1234 term.
  
To use, provide this command with an input ontology and specify which
types of definitions should be replaced (`-d` for DOT definitions or
`-s` for SUB definitions -- both options can be used simultaneously to
replace both types of definition in a single operation).

You may use the `-f` option to restrict the command to work on terms
that belong to a given namespace. For example, with `-f FBbt`, the
command will only rewrite definitions for terms in the
`http://purl.obolibrary.org/FBbt_` namespace.

The output ontology is the input ontology with the rewritten
definitions. In addition, you may also save the new definitions only in
a separate file with the `--write-to` option.

Copying
-------
The FlyBase ROBOT plugin is distributed under the terms of the MIT license.
