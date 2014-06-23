package org.apache.flume.sink;

import java.io.IOException;

import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.Transaction;
import org.apache.flume.conf.Configurable;
import org.apache.flume.sink.AbstractSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.flume.event.EventHelper;

import com.splunk.Service;
import com.splunk.Index;
import com.splunk.Args;
import com.splunk.ServiceArgs;

import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Random;
import java.text.SimpleDateFormat;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 * <p>
 * <b>Configuration options</b>
 * </p>
 * <table>
 * <tr>
 * <th>Parameter</th>
 * <th>Description</th>
 * <th>Unit (data type)</th>
 * <th>Default</th>
 * </tr>
 * <tr>
 * <td><tt></tt></td>
 * <td>The hostname to which events should be sent.</td>
 * <td>Hostname or IP (String)</td>
 * <td>none (required)</td>
 * </tr>
 * <td><tt>indexName</tt></td>
 * <td>The indexname on Splunk to get messages into.</td>
 * <td>indexName (String)</td>
 * <td>none (required)</td>
 * <tr>
 * <td><tt>userName</tt></td>
 * <td>username for Splunk.</td>
 * <td>userName (String)</td>
 * <td>none (required)</td>
 * </tr>
 * <tr>
 * <td><tt>password</tt></td>
 * <td>password for Splunk user(userName)</td>
 * <td>TCP port (int)</td>
 * <td>none (required)</td>
 * </tr>
 * <tr>
 * <td><tt>port</tt></td>
 * <td>The port to which events should be sent on <tt>hostname</tt>.</td>
 * <td>TCP port (int)</td>
 * <td>none (required)</td>
 * </tr>
 * <tr>
 * <td><tt>batch-size</tt></td>
 * <td>The maximum number of events to send per a transaction.</td>
 * <td>events (int)</td>
 * <td>100</td>
 * </tr>
 * </tr>
 */

public class SplunkSink extends AbstractSink implements Configurable {

        private static final Logger logger = LoggerFactory.getLogger(SplunkSink.class);
        private static final int defaultBatchSize = 100;
        private static final int defaultMessageSize = 0;
        private static final String defaultIndexName = "main";

        private Socket stream;
        private OutputStreamWriter splunkStream;
        private Index index;
        private String indexName = defaultIndexName;
        private String userName;
        private String password;
        private String host;
        private int port;
        private Args eventArgs = new Args();;

        private int batchSize = defaultBatchSize;
        private int messageSize = defaultMessageSize;

        public SplunkSink() {
        }

        @Override
        public void start() {
                super.start();
                ServiceArgs serviceArgs = new ServiceArgs();

                //Set Service's configurations
                serviceArgs.setUsername(userName);
                serviceArgs.setPassword(password);
                serviceArgs.setHost(host);
                serviceArgs.setPort(port);

                //connect Splunk Service
                Service service = Service.connect(serviceArgs);

                //Get Splunk Index (Reference : Splunk JAVA SDK)
                this.index = service.getIndexes().get(indexName);

                //If the Index set in the config is not valid,
                if(index == null){
                        System.out.println("Unable to open the Index(Splunk)  : " + indexName);
                        throw new RuntimeException("Unable to open the Index(Splunk)");
                }

                try {
                        //Attach Splunk Index and Get ouputstream to write Data on Splunk Index.
                        this.stream = index.attach();
                        splunkStream = new OutputStreamWriter(stream.getOutputStream(),
                                        "UTF8");
                } catch (IOException ioe) {
                        throw new RuntimeException("Unable to open a connection to Splunk");
                }
        }

        @Override
        public void stop() {
                try {
                        splunkStream.flush();
                        splunkStream.close();
                } catch (IOException ioe) {
                        System.out.println("Problem closing Splunk streams");
                }
        }

        @Override
        public void configure(Context context) {

            batchSize = context.getInteger("batch-size", defaultBatchSize);
            messageSize = context.getInteger("messageSize", defaultMessageSize);
            indexName = context.getString("indexName");
            userName = context.getString("userName");
            password = context.getString("password");
            host = context.getString("host");
            port = context.getInteger("port");

            //Debug console Log
            System.out.println("Splunk Sink Configuration Info");
            System.out.println("batch-size : " + batchSize);
            System.out.println("indexName : " + indexName);
            System.out.println("userName : " + userName);
            System.out.println("password : " + password);
            System.out.println("host : " + host);
            System.out.println("port : " + port);

        }

        @Override
        public Status process() throws EventDeliveryException {

                Status result = Status.READY;
                Channel channel = getChannel();
                Transaction transaction = channel.getTransaction();

                Event event = null;
                byte[] body;
                String eventstr = "";
                int eventCount;

                try {

                        //Transaction Begin
                        transaction.begin();
                        eventCount = this.batchSize;

                        //Loop (batchSize)
                        while(eventCount-- > 0){

                                //Get an event from designated channel
                                event = channel.take();

                                if (event != null) {

                                        /*
                                         * If you have something to write, add, or transform Message (including header messages),
                                         * Please, Add the code and try it
                                         */

                                        //Get Messages written in Body
                                        body = event.getBody();

                                        //Transform Bytes[] to String format
                                        eventstr = new String(body, 0, body.length);

                                        //Debug for check the message
                                        System.out.println("SplunkSink Message : " + eventstr);

                                        //Submit Message to Splunk Index
                                        index.submit(eventstr) ;
                                } else {
                                        // No event found, request back-off semantics from the sink
                                        // runner
                                        result = Status.BACKOFF;
                                        break;
                                }
                        }

                        transaction.commit();
                } catch (Exception ex) {
                        transaction.rollback();
                        throw new EventDeliveryException("Failed to log event: " + event, ex);
                } finally {
                        transaction.close();
                }
                return result;
        }        
}
