# archi-export-cli
Archi Command Line HTML Exporter

Produces an eclipse product (application) that runs on the command line to produce an HTML version of a .archimate file (Archi file format).

This requires all of the Archi code as part of the build when you export from eclipse into a product definition.

The functionality is identical to the HTML exporter plugin that ships as part of Archi (version 3.2).

Command line to execute:
java -XstartOnFirstThread -jar exporter/eclipse/plugins/org.eclipse.equinox.launcher_1.3.0.v20140415-2008.jar -consoleLog -console -noSplash -product com.redhat.ea.archimate.archireport.app <modelFile> <destination directory>

Note: -XstartOnFirstThread is required to run on Mac and optional on other systems
