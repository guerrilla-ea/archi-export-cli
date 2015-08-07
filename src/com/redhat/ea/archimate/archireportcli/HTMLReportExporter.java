/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.redhat.ea.archimate.archireportcli;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.Resource.Diagnostic;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroupFile;

import com.archimatetool.editor.diagram.util.DiagramUtils;
import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.editor.model.compatibility.CompatibilityHandlerException;
import com.archimatetool.editor.model.compatibility.IncompatibleModelException;
import com.archimatetool.editor.model.compatibility.ModelCompatibility;
//import com.archimatetool.editor.model.impl.EditorModelManager.ECoreAdapter;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateComponent;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.ModelVersion;
import com.archimatetool.model.util.ArchimateResourceFactory;
import com.archimatetool.reports.ArchimateEditorReportsPlugin;
import com.archimatetool.reports.html.Messages;

/**
 * Export model to HTML report
 * 
 * @author Jean-Baptiste Sarrodie
 * @author Quentin Varquet
 * @author Phillip Beauvoir
 */
public class HTMLReportExporter {

	private IArchimateModel fModel;

	private File fMainFolder;
	private File fElementsFolder;
	private File fViewsFolder;
	private File fImagesFolder;

	// Templates
	private ST stFrame;

	public void export(String modelFile, String path) throws IOException {
		
		fModel = openModel(modelFile);
		if(modelFile == null){
			return;
		}
		
		fMainFolder = askSaveFolder(path);
		if (fMainFolder == null) {
			return;
		}

		File file = createMainHTMLPage();

	}

	private IArchimateModel openModel(String modelFile) throws IOException {
		
		IArchimateModel model = null;
		
		File file = new File(modelFile);
		if (file == null || !file.exists()) {
			System.err.println(String.format("Cannot open %s. The file does not exist.", modelFile));
			return null;
		}

		// Ascertain if this is an archive file
		boolean useArchiveFormat = IArchiveManager.FACTORY.isArchiveFile(file);

		// Create the Resource
		Resource resource = ArchimateResourceFactory
				.createNewResource(useArchiveFormat ? IArchiveManager.FACTORY
						.createArchiveModelURI(file) : URI.createFileURI(file
						.getAbsolutePath()));

		// Check model compatibility
		ModelCompatibility modelCompatibility = new ModelCompatibility(resource);

		// Load the model file
		try {
			resource.load(null);
		} catch (IOException ex) {
			// Error occured loading model.
			try {
				modelCompatibility.checkErrors();
			} catch (IncompatibleModelException ex1) {
				// Was it a disaster?
				
				System.err.println(String.format("Cannot open %s. This model is incompatible.", modelFile));
				return null;
			}
		}

		model = (IArchimateModel) resource.getContents().get(0);


		// And then fix any backward compatibility issues
		try {
			modelCompatibility.fixCompatibility();
		} catch (CompatibilityHandlerException ex) {
		}

		model.setFile(file);
		model.setDefaults();
		
		//model.eAdapters().add(new ECoreAdapter());


		return model;

	}

	private File createMainHTMLPage() throws IOException {
		// Instantiate templates files
		File mainFile = new File(
				ArchimateEditorReportsPlugin.INSTANCE.getTemplatesFolder(),
				"st/main.stg"); //$NON-NLS-1$

		STGroupFile groupFile = new STGroupFile(mainFile.getAbsolutePath(),
				'^', '^');
		stFrame = groupFile.getInstanceOf("frame"); //$NON-NLS-1$
		ST stModel = groupFile.getInstanceOf("modelreport"); //$NON-NLS-1$

		// Copy HTML skeleton to target
		File srcDir = new File(
				ArchimateEditorReportsPlugin.INSTANCE.getTemplatesFolder(),
				"html"); //$NON-NLS-1$
		FileUtils.copyFolder(srcDir, fMainFolder);

		// Copy hints files from the help plug-in
		Bundle bundle = Platform.getBundle("com.archimatetool.help"); //$NON-NLS-1$
		URL url = FileLocator.resolve(bundle.getEntry("hints")); //$NON-NLS-1$
		FileUtils.copyFolder(new File(url.getPath()), new File(fMainFolder,
				"hints")); //$NON-NLS-1$

		// Set folders
		fElementsFolder = new File(fMainFolder, "elements"); //$NON-NLS-1$
		fElementsFolder.mkdirs(); // Make dir
		fViewsFolder = new File(fMainFolder, "views"); //$NON-NLS-1$
		fViewsFolder.mkdirs(); // Make dir
		fImagesFolder = new File(fMainFolder, "images"); //$NON-NLS-1$
		fImagesFolder.mkdirs(); // Make dir

		// write (elements).html
		writeElement(fModel, new File(fViewsFolder, "model.html")); //$NON-NLS-1$
		writeFolders(fModel.getFolders());

		// write (diagrams).html
		writeDiagrams();

		// Write root model.html
		File modeltreeF = new File(fMainFolder, "model.html"); //$NON-NLS-1$
		OutputStreamWriter modeltreeW = new OutputStreamWriter(
				new FileOutputStream(modeltreeF), "UTF8"); //$NON-NLS-1$
		stModel.add("model", fModel); //$NON-NLS-1$
		stModel.add("businessFolder", fModel.getFolder(FolderType.BUSINESS)); //$NON-NLS-1$
		stModel.add(
				"applicationFolder", fModel.getFolder(FolderType.APPLICATION)); //$NON-NLS-1$
		stModel.add("technologyFolder", fModel.getFolder(FolderType.TECHNOLOGY)); //$NON-NLS-1$
		stModel.add("motivationFolder", fModel.getFolder(FolderType.MOTIVATION)); //$NON-NLS-1$
		stModel.add(
				"implementationFolder", fModel.getFolder(FolderType.IMPLEMENTATION_MIGRATION)); //$NON-NLS-1$
		stModel.add("connectorsFolder", fModel.getFolder(FolderType.CONNECTORS)); //$NON-NLS-1$
		stModel.add("relationsFolder", fModel.getFolder(FolderType.RELATIONS)); //$NON-NLS-1$
		stModel.add("viewsFolder", fModel.getFolder(FolderType.DIAGRAMS)); //$NON-NLS-1$
		modeltreeW.write(stModel.render());
		modeltreeW.close();

		return new File(fMainFolder, "model.html"); //$NON-NLS-1$
	}

	private void writeFolders(EList<IFolder> folders) throws IOException {
		for (IFolder folder : folders) {
			writeFolder(folder);
		}
	}

	private void writeFolder(IFolder folder) throws IOException {
		writeElements(folder.getElements());
		writeFolders(folder.getFolders());
	}

	private void writeElements(List<EObject> list) throws IOException {
		if (!list.isEmpty()) {
			for (EObject object : list) {
				if (object instanceof IArchimateComponent) {
					writeElement(object, new File(fElementsFolder,
							((IIdentifier) object).getId() + ".html")); //$NON-NLS-1$
				}
			}
		}
	}

	private void writeElement(EObject component, File elementF)
			throws IOException {
		OutputStreamWriter elementW = new OutputStreamWriter(
				new FileOutputStream(elementF), "UTF8"); //$NON-NLS-1$
		stFrame.remove("element"); //$NON-NLS-1$
		// frame.remove("children");
		stFrame.add("element", component); //$NON-NLS-1$
		elementW.write(stFrame.render());
		elementW.close();
	}

	private void writeDiagrams() throws IOException {
		if (fModel.getDiagramModels().isEmpty()) {
			return;
		}

		saveDiagrams(fModel.getDiagramModels());

		for (IDiagramModel dm : fModel.getDiagramModels()) {
			File viewF = new File(fViewsFolder, dm.getId() + ".html"); //$NON-NLS-1$
			OutputStreamWriter viewW = new OutputStreamWriter(
					new FileOutputStream(viewF), "UTF8"); //$NON-NLS-1$
			stFrame.remove("element"); //$NON-NLS-1$
			stFrame.add("element", dm); //$NON-NLS-1$
			viewW.write(stFrame.render());
			viewW.close();
		}
	}

	private Hashtable<IDiagramModel, String> saveDiagrams(
			List<IDiagramModel> list) {
		Hashtable<IDiagramModel, String> table = new Hashtable<IDiagramModel, String>();
		int i = 1;

		for (IDiagramModel dm : list) {
			Image image = DiagramUtils.createImage(dm, 1, 10);
			String diagramName = dm.getId();
			if (StringUtils.isSet(diagramName)) {
				diagramName = FileUtils.getValidFileName(diagramName);
				int j = 2;
				String s = diagramName + ".png"; //$NON-NLS-1$
				while (table.containsValue(s)) {
					s = diagramName + "_" + j++ + ".png"; //$NON-NLS-1$ //$NON-NLS-2$
				}
				diagramName = s;
			} else {
				diagramName = Messages.HTMLReportExporter_1
						+ " " + i++ + ".png"; //$NON-NLS-1$//$NON-NLS-2$
			}

			table.put(dm, diagramName);

			try {
				ImageLoader loader = new ImageLoader();
				loader.data = new ImageData[] { image.getImageData() };
				File file = new File(fImagesFolder, diagramName);
				loader.save(file.getAbsolutePath(), SWT.IMAGE_PNG);
			} finally {
				image.dispose();
			}
		}

		return table;
	}

	private File askSaveFolder(String path) {
		if (path == null) {
			System.err.println(String.format("%s is an invalid path.", path));
			return null;
		}

		File folder = new File(path);
		folder.mkdirs();

		return folder;
	}
}
