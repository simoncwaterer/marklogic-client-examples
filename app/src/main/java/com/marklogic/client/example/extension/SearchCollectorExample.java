/*
 * Copyright (c) 2022 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marklogic.client.example.extension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.DatabaseClientFactory.Authentication;
import com.marklogic.client.FailedRequestException;
import com.marklogic.client.ForbiddenUserException;
import com.marklogic.client.ResourceNotFoundException;
import com.marklogic.client.ResourceNotResendableException;
import com.marklogic.client.admin.ExtensionMetadata;
import com.marklogic.client.admin.MethodType;
import com.marklogic.client.admin.QueryOptionsManager;
import com.marklogic.client.admin.ResourceExtensionsManager;
import com.marklogic.client.admin.ResourceExtensionsManager.MethodParameters;
import com.marklogic.client.admin.ServerConfigurationManager;
import com.marklogic.client.document.XMLDocumentManager;
import com.marklogic.client.example.cookbook.Util;
import com.marklogic.client.example.cookbook.Util.ExampleProperties;
import com.marklogic.client.example.extension.SearchCollector.CollectorResults;
import com.marklogic.client.io.InputStreamHandle;
import com.marklogic.client.io.StringHandle;

/**
 * SearchCollectorExample illustrates reading a page of documents
 * qualified by a query using the SearchCollector class example
 * of a Resource Extension.
 */
public class SearchCollectorExample {
  static final private String OPTIONS_NAME = "collect";

  static final private String[] filenames = {"curbappeal.xml", "flipper.xml", "justintime.xml"};

  public static void main(String[] args)
    throws IOException, ParserConfigurationException, ResourceNotFoundException, ForbiddenUserException, FailedRequestException, ResourceNotResendableException
  {
    run(Util.loadProperties());
  }

  // install and then use the resource extension
  public static void run(ExampleProperties props)
    throws IOException, ParserConfigurationException, ResourceNotFoundException, ForbiddenUserException, FailedRequestException, ResourceNotResendableException
  {
    System.out.println("example: "+SearchCollectorExample.class.getName());

    configureExample(props.host, props.port,
      props.adminUser, props.adminPassword, props.authType);

    useResource(props.host, props.port,
      props.writerUser, props.writerPassword, props.authType);

    tearDownExample(props.host, props.port,
      props.adminUser, props.adminPassword, props.authType);
  }

  // set up the query options for the collecting search
  public static void configureExample(String host, int port, String user, String password, Authentication authType)
    throws IOException, ResourceNotFoundException, ForbiddenUserException, FailedRequestException, ResourceNotResendableException
  {
    // create the client
    DatabaseClient client = DatabaseClientFactory.newClient(host, port, user, password, authType);

    installResourceExtension(client);

    configureQueryOptions(client);

    setUpExample(client);

    // release the client
    client.release();
  }

  // install the resource extension on the server
  public static void installResourceExtension(DatabaseClient client)
    throws IOException
  {
    // create a manager for resource extensions
    ResourceExtensionsManager resourceMgr = client.newServerConfigManager().newResourceExtensionsManager();

    // specify metadata about the resource extension
    ExtensionMetadata metadata = new ExtensionMetadata();
    metadata.setTitle("Search Collector Resource Services");
    metadata.setDescription("This plugin collects documents qualified by search");
    metadata.setProvider("MarkLogic");
    metadata.setVersion("0.1");

    // acquire the resource extension source code
    try ( InputStream sourceStream = Util.openStream("scripts"+File.separator+SearchCollector.NAME+".xqy") ) {
      if (sourceStream == null) throw new RuntimeException("Could not read example resource extension");

      // create a handle on the extension source code
      InputStreamHandle handle = new InputStreamHandle(sourceStream);
      handle.set(sourceStream);

      MethodParameters getParams = new MethodParameters(MethodType.GET);
      getParams.add("format",     "xs:string");
      getParams.add("options",    "xs:string");
      getParams.add("pageLength", "xs:string");
      getParams.add("start",      "xs:string");
      getParams.add("view",       "xs:string");

      getParams.add("q",          "xs:string");

      MethodParameters postParams = new MethodParameters(MethodType.POST);
      postParams.add("format",     "xs:string");
      postParams.add("options",    "xs:string");
      postParams.add("pageLength", "xs:string");
      postParams.add("start",      "xs:string");
      postParams.add("view",       "xs:string");

      // write the resource extension to the database
      resourceMgr.writeServices(SearchCollector.NAME, handle, metadata, getParams, postParams);
    }

    System.out.println("Installed the resource extension on the server");
  }

  // set up the query options for the collecting search
  public static void configureQueryOptions(DatabaseClient client)
    throws FailedRequestException, ForbiddenUserException, ResourceNotFoundException, ResourceNotResendableException
  {
    // create a manager for writing query options
    QueryOptionsManager optionsMgr = client.newServerConfigManager().newQueryOptionsManager();

    // create the query options
    String options =
      "<search:options "+
        "xmlns:search='http://marklogic.com/appservices/search'>"+
        "<search:constraint name='industry'>"+
        "<search:value>"+
        "<search:element name='industry' ns=''/>"+
        "</search:value>"+
        "</search:constraint>"+
        "<search:return-aggregates>false</search:return-aggregates>"+
        "<search:return-constraints>false</search:return-constraints>"+
        "<search:return-facets>false</search:return-facets>"+
        "<search:return-frequencies>false</search:return-frequencies>"+
        "<search:return-metrics>false</search:return-metrics>"+
        "<search:return-plan>false</search:return-plan>"+
        "<search:return-qtext>false</search:return-qtext>"+
        "<search:return-query>false</search:return-query>"+
        "<search:return-similar>true</search:return-similar>"+
        "<search:return-values>false</search:return-values>"+
        "<search:return-results>false</search:return-results>"+
        "<search:transform-results apply='empty-snippet'/>"+
        "</search:options>";

    // create a handle to send the query options
    StringHandle writeHandle = new StringHandle(options);

    // write the query options to the database
    optionsMgr.writeOptions(OPTIONS_NAME, writeHandle);

    System.out.println("Configured the query options on the server");
  }

  // set up by writing the document content and options used in the example query
  public static void setUpExample(DatabaseClient client)
    throws IOException, ResourceNotFoundException, ForbiddenUserException, FailedRequestException
  {
    XMLDocumentManager docMgr = client.newXMLDocumentManager();

    try ( InputStreamHandle contentHandle = new InputStreamHandle() ) {

      for (String filename: filenames) {
        InputStream docStream =  Util.openStream(
          "data"+File.separator+filename);
        if (docStream == null) throw new IOException("Could not read document example");

        contentHandle.set(docStream);

        docMgr.write("/example/"+filename, contentHandle);
      }
    }
  }

  // use the resource manager
  public static void useResource(String host, int port, String user, String password, Authentication authType)
    throws IOException, ParserConfigurationException
  {
    // create the client
    DatabaseClient client = DatabaseClientFactory.newClient(host, port, user, password, authType);

    // create the search collector
    SearchCollector collector = new SearchCollector(client);

    // search for documents to collect
    CollectorResults results = collector.collect(
      "neighborhood industry:\"Real Estate\"", 1, OPTIONS_NAME
    );

    System.out.println();
    System.out.println("search results:");
    System.out.println(results.getSearchResult(new StringHandle()).get());

    // iterate over the collected documents
    StringHandle resultHandle = new StringHandle();
    for (int i=1; results.hasNext(); i++) {
      System.out.println();
      System.out.println("matched document "+i+":");
      System.out.println(results.next(resultHandle).get());
    }

    // release the iterator resources
    results.close();

    // release the client
    client.release();
  }

  // clean up by deleting the example resource extension
  public static void tearDownExample(
    String host, int port, String user, String password, Authentication authType)
    throws ResourceNotFoundException, ForbiddenUserException, FailedRequestException
  {
    DatabaseClient client = DatabaseClientFactory.newClient(host, port, user, password, authType);

    XMLDocumentManager docMgr = client.newXMLDocumentManager();
    for (String filename: filenames) {
      docMgr.delete("/example/"+filename);
    }

    ServerConfigurationManager confMgr = client.newServerConfigManager();

    QueryOptionsManager optionsMgr = confMgr.newQueryOptionsManager();
    optionsMgr.deleteOptions(OPTIONS_NAME);

    ResourceExtensionsManager resourceMgr = confMgr.newResourceExtensionsManager();
    resourceMgr.deleteServices(SearchCollector.NAME);

    client.release();
  }
}
