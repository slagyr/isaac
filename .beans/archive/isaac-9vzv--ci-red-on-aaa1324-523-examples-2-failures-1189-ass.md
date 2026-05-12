---
# isaac-9vzv
title: "CI red on aaa1324: 523 examples, 2 failures, 1189 assertions"
status: completed
type: bug
priority: high
created_at: 2026-05-11T18:36:30Z
updated_at: 2026-05-11T20:36:20Z
---

## Description

CI verification failed on push to main.

- Commit: aaa13240d9048ea34aeb244117b45cc831b265ae
- Short SHA: aaa1324
- Branch: main
- Repository: slagyr/isaac
- GitHub actor: slagyr
- Commit author: Micah <micahmartin@gmail.com>
- Run: https://github.com/slagyr/isaac/actions/runs/25689663606

Summary:
523 examples, 2 failures, 1189 assertions

Failure excerpt:
```text
Downloading: commons-io/commons-io/2.17.0/commons-io-2.17.0.jar from central
Downloading: org/apache/maven/maven-settings-builder/3.8.6/maven-settings-builder-3.8.6.jar from central
Downloading: com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar from central
Downloading: org/clojure/tools.namespace/1.5.0/tools.namespace-1.5.0.jar from central
Downloading: org/eclipse/jetty/websocket/websocket-core-server/11.0.24/websocket-core-server-11.0.24.jar from central
Downloading: org/apache/commons/commons-fileupload2-core/2.0.0-M1/commons-fileupload2-core-2.0.0-M1.jar from central
Downloading: com/fasterxml/jackson/core/jackson-core/2.17.0/jackson-core-2.17.0.jar from central
Downloading: org/eclipse/jetty/websocket/websocket-servlet/11.0.24/websocket-servlet-11.0.24.jar from central
Downloading: speclj/speclj/3.13.0/speclj-3.13.0.jar from clojars
Downloading: org/ring-clojure/ring-jakarta-servlet/1.13.0/ring-jakarta-servlet-1.13.0.jar from clojars
Downloading: org/ow2/asm/asm/9.2/asm-9.2.jar from central
Downloading: trptcolin/versioneer/0.2.0/versioneer-0.2.0.jar from clojars
Downloading: org/apache/maven/maven-settings/3.8.6/maven-settings-3.8.6.jar from central
Downloading: org/eclipse/jetty/websocket/websocket-jetty-server/11.0.24/websocket-jetty-server-11.0.24.jar from central
Downloading: cljsjs/react/17.0.2-0/react-17.0.2-0.jar from clojars
Downloading: org/eclipse/jetty/jetty-unixdomain-server/11.0.24/jetty-unixdomain-server-11.0.24.jar from central
Downloading: org/eclipse/jetty/jetty-security/11.0.24/jetty-security-11.0.24.jar from central
Downloading: org/apache/httpcomponents/httpcore/4.4.15/httpcore-4.4.15.jar from central
Downloading: org/codehaus/plexus/plexus-sec-dispatcher/2.0/plexus-sec-dispatcher-2.0.jar from central
Downloading: org/apache/maven/maven-core/3.8.6/maven-core-3.8.6.jar from central
Downloading: instaparse/instaparse/1.4.8/instaparse-1.4.8.jar from clojars
Downloading: com/cognitect/transit-js/0.8.874/transit-js-0.8.874.jar from central
Downloading: org/bouncycastle/bcprov-jdk18on/1.78.1/bcprov-jdk18on-1.78.1.jar from central
Downloading: com/taoensso/truss/2.2.0/truss-2.2.0.jar from clojars
Downloading: org/apache/maven/resolver/maven-resolver-api/1.8.2/maven-resolver-api-1.8.2.jar from central
Downloading: hiccup/hiccup/1.0.5/hiccup-1.0.5.jar from clojars
Downloading: org/apache/maven/maven-resolver-provider/3.8.6/maven-resolver-provider-3.8.6.jar from central
Downloading: com/cognitect/http-client/1.0.115/http-client-1.0.115.jar from central
Downloading: org/javassist/javassist/3.18.1-GA/javassist-3.18.1-GA.jar from central
Downloading: org/eclipse/jetty/jetty-webapp/11.0.24/jetty-webapp-11.0.24.jar from central
Downloading: org/apache/maven/shared/maven-shared-utils/3.3.4/maven-shared-utils-3.3.4.jar from central
Downloading: org/clojure/java.classpath/1.1.0/java.classpath-1.1.0.jar from central
Downloading: ns-tracker/ns-tracker/1.0.0/ns-tracker-1.0.0.jar from clojars
Downloading: clout/clout/2.2.1/clout-2.2.1.jar from clojars
Downloading: org/clojure/tools.deps.alpha/0.15.1254/tools.deps.alpha-0.15.1254.jar from central
Downloading: com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar from central
Downloading: com/google/guava/guava/31.1-android/guava-31.1-android.jar from central
Downloading: org/clojure/data.xml/0.2.0-alpha8/data.xml-0.2.0-alpha8.jar from central
Downloading: org/apache/maven/resolver/maven-resolver-spi/1.8.2/maven-resolver-spi-1.8.2.jar from central
Downloading: reagent/reagent/1.2.0/reagent-1.2.0.jar from clojars
Downloading: org/msgpack/msgpack/0.6.12/msgpack-0.6.12.jar from central
Downloading: com/google/j2objc/j2objc-annotations/1.3/j2objc-annotations-1.3.jar from central
Downloading: com/taoensso/timbre/6.8.0/timbre-6.8.0.jar from clojars
Downloading: org/slf4j/slf4j-nop/2.0.17/slf4j-nop-2.0.17.jar from central
Downloading: com/cognitect/transit-clj/1.0.333/transit-clj-1.0.333.jar from central
Downloading: org/codehaus/plexus/plexus-classworlds/2.6.0/plexus-classworlds-2.6.0.jar from central
Downloading: crypto-random/crypto-random/1.2.1/crypto-random-1.2.1.jar from clojars
Downloading: ring/ring-codec/1.2.0/ring-codec-1.2.0.jar from clojars
Downloading: org/codehaus/plexus/plexus-interpolation/1.26/plexus-interpolation-1.26.jar from central
Downloading: org/apache/httpcomponents/httpclient/4.5.13/httpclient-4.5.13.jar from central
Downloading: ring/ring-anti-forgery/1.4.0/ring-anti-forgery-1.4.0.jar from clojars
Downloading: org/ring-clojure/ring-websocket-protocols/1.13.0/ring-websocket-protocols-1.13.0.jar from clojars
Downloading: crypto-equality/crypto-equality/1.0.1/crypto-equality-1.0.1.jar from clojars
Downloading: org/checkerframework/checker-qual/3.12.0/checker-qual-3.12.0.jar from central
Downloading: com/google/inject/guice/4.2.2/guice-4.2.2-no_aop.jar from central
Downloading: cheshire/cheshire/5.13.0/cheshire-5.13.0.jar from clojars
Downloading: tigris/tigris/0.1.2/tigris-0.1.2.jar from clojars
Downloading: org/eclipse/jetty/jetty-client/9.4.48.v20220622/jetty-client-9.4.48.v20220622.jar from central
Downloading: babashka/process/0.5.22/process-0.5.22.jar from clojars
Downloading: com/andrewmcveigh/cljs-time/0.5.2/cljs-time-0.5.2.jar from clojars
Downloading: org/eclipse/jetty/jetty-io/11.0.24/jetty-io-11.0.24.jar from central
Downloading: org/clojure/tools.reader/1.4.2/tools.reader-1.4.2.jar from central
Downloading: org/ring-clojure/ring-core-protocols/1.13.0/ring-core-protocols-1.13.0.jar from clojars
Downloading: org/clojure/tools.gitlibs/2.4.181/tools.gitlibs-2.4.181.jar from central
Downloading: org/apache/maven/resolver/maven-resolver-connector-basic/1.8.2/maven-resolver-connector-basic-1.8.2.jar from central
Downloading: org/babashka/http-client/0.4.22/http-client-0.4.22.jar from clojars
Downloading: com/cognitect/aws/s3/822.2.1145.0/s3-822.2.1145.0.jar from central
Downloading: clj-stacktrace/clj-stacktrace/0.2.8/clj-stacktrace-0.2.8.jar from clojars
Downloading: org/apache/maven/resolver/maven-resolver-impl/1.8.2/maven-resolver-impl-1.8.2.jar from central
Downloading: org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar from central
Downloading: org/apache/maven/maven-model/3.8.6/maven-model-3.8.6.jar from central
Downloading: buddy/buddy-sign/3.6.1-359/buddy-sign-3.6.1-359.jar from clojars
Downloading: org/eclipse/sisu/org.eclipse.sisu.inject/0.3.5/org.eclipse.sisu.inject-0.3.5.jar from central
Downloading: org/apache/maven/resolver/maven-resolver-util/1.8.2/maven-resolver-util-1.8.2.jar from central
Downloading: babashka/fs/0.4.18/fs-0.4.18.jar from clojars
Downloading: org/apache/maven/resolver/maven-resolver-named-locks/1.8.2/maven-resolver-named-locks-1.8.2.jar from central
Downloading: org/apache/maven/maven-repository-metadata/3.8.6/maven-repository-metadata-3.8.6.jar from central
Downloading: org/clojure/core.memoize/1.1.266/core.memoize-1.1.266.jar from central
Downloading: ring/ring/1.13.0/ring-1.13.0.jar from clojars
Downloading: org/clojure/data.priority-map/1.2.0/data.priority-map-1.2.0.jar from central
Downloading: org/eclipse/jetty/jetty-server/11.0.24/jetty-server-11.0.24.jar from central
Downloading: aopalliance/aopalliance/1.0/aopalliance-1.0.jar from central
Downloading: com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar from central
Downloading: org/apache/maven/maven-builder-support/3.8.6/maven-builder-support-3.8.6.jar from central
Downloading: org/clojure/core.cache/1.1.234/core.cache-1.1.234.jar from central
Downloading: ring/ring-core/1.13.0/ring-core-1.13.0.jar from clojars
Downloading: org/babashka/json/0.1.6/json-0.1.6.jar from clojars
Downloading: medley/medley/1.4.0/medley-1.4.0.jar from clojars
Downloading: org/apache/maven/maven-plugin-api/3.8.6/maven-plugin-api-3.8.6.jar from central
Downloading: org/clojure/core.async/1.7.701/core.async-1.7.701.jar from central
Downloading: com/fasterxml/jackson/dataformat/jackson-dataformat-smile/2.17.0/jackson-dataformat-smile-2.17.0.jar from central
Downloading: org/apache/maven/maven-artifact/3.8.6/maven-artifact-3.8.6.jar from central
Downloading: javax/xml/bind/jaxb-api/2.4.0-b180830.0359/jaxb-api-2.4.0-b180830.0359.jar from central
Downloading: org/clj-commons/pretty/3.6.3/pretty-3.6.3.jar from clojars
Downloading: http-kit/http-kit/2.8.0/http-kit-2.8.0.jar from clojars
.................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................

Finished in 3.37117 seconds
1489 examples, 0 failures, 2960 assertions

................................................................Downloading: org/clojure/tools.reader/1.4.0/tools.reader-1.4.0.pom from central
Downloading: org/clojure/tools.reader/1.5.2/tools.reader-1.5.2.pom from central
Downloading: org/eclipse/jetty/jetty-http/9.4.48.v20220622/jetty-http-9.4.48.v20220622.pom from central
Downloading: org/eclipse/jetty/jetty-io/9.4.48.v20220622/jetty-io-9.4.48.v20220622.pom from central
Downloading: org/clojure/tools.reader/1.5.2/tools.reader-1.5.2.jar from central
.....................................................................................................................................................................................................................................................................................................................................................................................................................................................................FF....

Failures:

  1) Built-in grep tool grep output_mode files_with_matches returns paths only
     Expected truthy but was: false
     /home/runner/.gitlibs/libs/io.github.slagyr/gherclj/de202c862ae7745f20aea5c3741ffc817712a7e2/src/gherclj/frameworks/clojure/speclj.clj:87

  2) Built-in grep tool grep output_mode count returns match count per file
     Expected truthy but was: false
     /home/runner/.gitlibs/libs/io.github.slagyr/gherclj/de202c862ae7745f20aea5c3741ffc817712a7e2/src/gherclj/frameworks/clojure/speclj.clj:87

Finished in 17.84406 seconds
523 examples, 2 failures, 1189 assertions
Error while executing task: ci
```

