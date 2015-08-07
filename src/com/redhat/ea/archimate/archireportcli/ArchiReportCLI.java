package com.redhat.ea.archimate.archireportcli;

import java.io.IOException;
import java.util.Map;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

public class ArchiReportCLI implements IApplication {

	@Override
	public Object start(IApplicationContext context) throws Exception {
		
		String[] cmdArgs = (String[])context.getArguments().get(context.APPLICATION_ARGS);
		if(cmdArgs!=null && cmdArgs.length!=2){
			System.err.println("Usage: ArchiReportCLI modeFile exportPath");
			return EXIT_OK;
		}
		
		//first parameter is the model file
		//second parameter is the destination folder
		try {
            HTMLReportExporter exporter = new HTMLReportExporter();
            exporter.export(cmdArgs[0], cmdArgs[1]);
        }
        catch(IOException ex) {
        	System.err.println("Error Exporting");
        	System.err.println(ex.getMessage());
            ex.printStackTrace();
        }
		
		return EXIT_OK;
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}
	
}
