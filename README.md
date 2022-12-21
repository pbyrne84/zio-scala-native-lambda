# zio scala native lambda


This project is an extension of what was learnt from <https://github.com/pbyrne84/scala_native_lambda_test>.
  It is composed of the following parts.

## Overview

1. A [pulumi](pulumi) config to build an AWS environment with an SQS queue that fires the lambda with the message.
2. A [DockerFile](Dockerfile) which creates an Amazon Linux image with Graal and Scala installed to build an image
   compatible with Amazon Lambdas.
3. A ZIO Scala project which reads incoming messages and registers the message is processed, if the message is not 
   registered with an invocation response the lambda goes around in circles. The request id is attained from the
   response header, this allows us to mark the message as failed if we cannot decode the message.
4. All logging is done by **Logback** via **SL4J** using the **LogstashEncoder**. This handles all the logging as 
   JSON which allows us to structure our logging in an easy to import way into something like Kibana. Using **SL4J**
   allows any java libs to also output in this log format though to get the MDC goodness some hackery will have to be
   done for non log calls i.e <https://github.com/pbyrne84/zio2playground/blob/48fe8b997b683a3852f00428b8d6f870d7695a3d/src/main/scala/com/github/pbyrne84/zio2playground/logging/ZIOHack.scala#L18>. Mirrors a ZIO.attempt call but adds the mdc stuff for java calls.
5.  Tracing of a **trace_id** which gets passed to every logging call using a log annotation **LogAnnotation[String]**.
    This is handy as we can scan for logs with a level of ERROR and then collate the trace_ids for analysis. It
    appears in logs like :-
```json
    {
       "@timestamp": "2022-12-20T16:02:46.664Z",
       "@version": "1",
       "message": "calling lambda using GET:http://127.0.0.1:9001/2018-06-01/runtime/invocation/next",
       "logger_name": "com.github.pbyrne84.zioscalanativelambda.client.LambdaHttpClient",
       "thread_name": "ZScheduler-Worker-0",
       "level": "INFO",
       "level_value": 20000,
       "trace_id": "7dcc0588-c9b2-4c0e-bf89-6643459eebc7"
   }  
```
