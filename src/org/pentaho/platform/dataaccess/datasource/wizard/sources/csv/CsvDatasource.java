package org.pentaho.platform.dataaccess.datasource.wizard.sources.csv;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import org.pentaho.agilebi.modeler.ModelerWorkspace;
import org.pentaho.metadata.model.Domain;
import org.pentaho.platform.dataaccess.datasource.beans.BogoPojo;
import org.pentaho.platform.dataaccess.datasource.wizard.IDatasourceSummary;
import org.pentaho.platform.dataaccess.datasource.wizard.IWizardDatasource;
import org.pentaho.platform.dataaccess.datasource.wizard.IWizardStep;
import org.pentaho.platform.dataaccess.datasource.wizard.controllers.MessageHandler;
import org.pentaho.platform.dataaccess.datasource.wizard.models.ColumnInfo;
import org.pentaho.platform.dataaccess.datasource.wizard.models.CsvTransformGeneratorException;
import org.pentaho.platform.dataaccess.datasource.wizard.models.DatasourceDTO;
import org.pentaho.platform.dataaccess.datasource.wizard.models.DatasourceModel;
import org.pentaho.platform.dataaccess.datasource.wizard.service.gwt.ICsvDatasourceService;
import org.pentaho.platform.dataaccess.datasource.wizard.service.gwt.ICsvDatasourceServiceAsync;
import org.pentaho.platform.dataaccess.datasource.wizard.steps.StageDataStep;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulEventSourceAdapter;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.XulServiceCallback;
import org.pentaho.ui.xul.binding.BindingFactory;
import org.pentaho.ui.xul.stereotype.Bindable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: nbaker
 * Date: 3/22/11
 */
public class CsvDatasource extends XulEventSourceAdapter implements IWizardDatasource {


  private ICsvDatasourceServiceAsync csvDatasourceService;

  private DatasourceModel datasourceModel;
  private MessageHandler handler;
  public FileTransformStats stats;
  private StageDataStep stageStep;
  private CsvPhysicalStep csvStep;
  private DatasourceModel model;
  private BindingFactory bindingFactory;
  private XulDomContainer container;

  public CsvDatasource(DatasourceModel datasourceModel){
    this.datasourceModel = datasourceModel;

    this.csvDatasourceService = (ICsvDatasourceServiceAsync) GWT.create(ICsvDatasourceService.class);
    ServiceDefTarget endpoint = (ServiceDefTarget) this.csvDatasourceService;
    endpoint.setServiceEntryPoint(getDatasourceURL());
    csvStep = new CsvPhysicalStep(csvDatasourceService);
    stageStep = new StageDataStep(csvDatasourceService);

    csvDatasourceService.gwtWorkaround(new BogoPojo(), new AsyncCallback<BogoPojo>(){

      @Override
      public void onFailure(Throwable throwable) {

      }

      @Override
      public void onSuccess(BogoPojo bogoPojo) {
        bogoPojo.getAggType();
      }
    });
  }

  public String getDatasourceURL(){
	  String moduleUrl = GWT.getModuleBaseURL();
    if (moduleUrl.indexOf("content") > -1) {//$NON-NLS-1$
        //we are running the client in the context of a BI Server plugin, so
        //point the request to the GWT rpc proxy servlet
        String baseUrl = moduleUrl.substring(0, moduleUrl.indexOf("content"));//$NON-NLS-1$
        //NOTE: the dispatch URL ("connectionService") must match the bean id for
        //this service object in your plugin.xml.  "gwtrpc" is the servlet
        //that handles plugin gwt rpc requests in the BI Server.
        return baseUrl + "gwtrpc/CsvDatasourceService";//$NON-NLS-1$
      }
      //we are running this client in hosted mode, so point to the servlet
      //defined in war/WEB-INF/web.xml
      return moduleUrl + "CsvDatasourceService";//$NON-NLS-1$
  }

  @Override
  public void activating() throws XulException {
    csvStep.activating();
    stageStep.activating();
  }


  @Override
  public void deactivating() {
    csvStep.deactivate();
    stageStep.deactivate();
  }

  @Override
  public void init() throws XulException {
    //TODO: fix this initialization craziness

    csvStep.setBindingFactory(bindingFactory);
    csvStep.setDocument(container.getDocumentRoot());
    csvStep.init(container);

    stageStep.setBindingFactory(bindingFactory);
    stageStep.setDocument(container.getDocumentRoot());
    stageStep.init(container);
  }

  @Override
  @Bindable
  public String getName() {
    return "CSV file"; //TODO: i18n
  }

  @Override
  public List<IWizardStep> getSteps() {
    List<IWizardStep> steps = new ArrayList<IWizardStep>();
    steps.add(csvStep);
    steps.add(stageStep);
    return steps;
  }

  @Override
  public void onFinish(final XulServiceCallback<IDatasourceSummary> callback) {

    datasourceModel.getGuiStateModel().setDataStagingComplete(false);
    setColumnIdsToColumnNames();

    String name = datasourceModel.getDatasourceName().replace(".", "_").replace(" ", "_");
    // set the modelInfo.stageTableName to the database table name generated from the datasourceName
    datasourceModel.getModelInfo().setStageTableName(datasourceModel.generateTableName());
    String tmpFileName = datasourceModel.getModelInfo().getFileInfo().getTmpFilename();
    String fileName = datasourceModel.getModelInfo().getFileInfo().getFileName();
    if(fileName == null && tmpFileName != null && tmpFileName.endsWith(".tmp")) {
      tmpFileName = tmpFileName.substring(0, tmpFileName.lastIndexOf(".tmp"));
      datasourceModel.getModelInfo().getFileInfo().setFileName(tmpFileName);
    }

    datasourceModel.getModelInfo().setDatasourceName(datasourceModel.getDatasourceName());
    csvDatasourceService.generateDomain(datasourceModel.getModelInfo(), new AsyncCallback<IDatasourceSummary>(){
      public void onFailure(Throwable th) {
        MessageHandler.getInstance().closeWaitingDialog();
        if (th instanceof CsvTransformGeneratorException) {
          MessageHandler.getInstance().showErrorDetailsDialog(MessageHandler.getInstance().messages.getString("ERROR"), th.getMessage(), ((CsvTransformGeneratorException)th).getCauseMessage() + ((CsvTransformGeneratorException)th).getCauseStackTrace());
        } else {
          MessageHandler.getInstance().showErrorDialog(MessageHandler.getInstance().messages.getString("ERROR"), th.getMessage());
        }
        th.printStackTrace();
      }

      public void onSuccess(IDatasourceSummary stats) {
        CsvDatasource.this.stats = (FileTransformStats) stats;

        MessageHandler.getInstance().closeWaitingDialog();
        callback.success(stats);
      }
    });
  }
  private void setColumnIdsToColumnNames() {
    for (ColumnInfo ci : datasourceModel.getModelInfo().getColumns()) {
      ci.setId(ci.getTitle());
    }
  }

  @Override
  public void setMessageHandler(MessageHandler handler) {
    this.handler = handler;
  }


//  private void generateDomain(String connectionName, String tableName, String query, final XulServiceCallback<String> callback){
//    modelerService.generateDomain(connectionName, tableName, dbType, query, datasourceModel.getDatasourceName(), new XulServiceCallback<Domain>(){
//      public void success( final Domain domain) {
//
//
//
//        datasourceService.serializeModelState(DatasourceDTO.generateDTO(datasourceModel), new XulServiceCallback<String>(){
//          public void success(String retVal) {
//            domain.getLogicalModels().get(0).setProperty("datasourceModel", retVal);
//
//          }
//
//          public void error(String message, Throwable error) {
//            MessageHandler.getInstance().closeWaitingDialog();
//            MessageHandler.getInstance().showErrorDialog(message, error.getMessage());
//            error.printStackTrace();
//          }
//        });
//
//      }
//
//      public void error(String s, Throwable throwable ) {
//        MessageHandler.getInstance().closeWaitingDialog();
//        MessageHandler.getInstance().showErrorDialog(s, throwable.getMessage());
//        throwable.printStackTrace();
//      }
//    });
//  }


  @Override
  public String getId() {
    return "CSV";
  }

  @Override
  public void setDatasourceModel(DatasourceModel model) {
    this.model = model;
    this.csvStep.setDatasourceModel(model);
    this.stageStep.setDatasourceModel(model);
  }

  @Override
  public void setBindingFactory(BindingFactory bindingFactory) {
    this.bindingFactory = bindingFactory;
  }

  @Override
  public void setXulDomContainer(XulDomContainer container) {
    this.container = container;
  }
}
