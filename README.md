flume_splunk_sink
=================

flume sink plug-in for Splunk

	Maven build (pom.xml)

	<dependency>
	  <groupId>splunk</groupId>
	  <artifactId>splunk</artifactId>
	  <version>1.2.2</version>
	</dependency>


Running Script 
run : ./bin/flume-ng agent -c conf -f ./conf/flume.conf_splunk -n agent -Dflume.root.logger=DEBUG,console
