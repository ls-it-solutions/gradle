/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.resource.transport.http

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.BlockingHttpsServer
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import javax.net.ssl.SSLContext
import java.security.Provider
import java.security.Security

class SSLTest extends Specification {
    @Rule
    SetSystemProperties properties = new SetSystemProperties()
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    @Rule
    TestResources resources = new TestResources(temporaryFolder)
    @Rule
    BlockingHttpsServer server = new BlockingHttpsServer()

    class FakeProvider extends Provider {
        public FakeProvider() {
            super("FakeProvider", 1.0, "FakeProvider");
            putService(new Service(this, "KeyStore", "FAKEKS", "org.gradle.internal.resource.transport.http.FakeKeyStore", null, null));
        }
    }


    def "test ssl"() {
        given:

        Security.addProvider(new FakeProvider());
        TestKeyStore keyStore = TestKeyStore.init(resources.dir, "FAKEKS")

        def props = System.getProperties()
        props.put("javax.net.ssl.keyStoreType", "FAKEKS")
        props.put("javax.net.ssl.keyStore", keyStore.keyStore.absolutePath)
        props.put("javax.net.ssl.keyStorePassword", keyStore.keyStorePassword)
        props.put("javax.net.ssl.trustStoreType", "FAKEKS")
        props.put("javax.net.ssl.trustStore", keyStore.keyStore.absolutePath)
        props.put("javax.net.ssl.trustStorePassword", keyStore.keyStorePassword)

        SslContextFactory factory = new DefaultSslContextFactory()
        SSLContext factoryContext = factory.createSslContext()
        SSLContext defaultContext = keyStore.asSSLContext()
        println("$factoryContext, $defaultContext")


        HttpSettings settings = DefaultHttpSettings.builder()
            .withAuthenticationSettings([])
            .withRedirectVerifier {}
            .withSslContextFactory(factory)
            .build()

        HttpClientHelper client = new HttpClientHelper(new DocumentationRegistry(), settings)

        server.configure(keyStore)
        server.start()
        server.expect('/test')

        when:
        client.performGet("${server.getUri()}/test", false)

        then:
        noExceptionThrown()

        cleanup:
        client.close()
    }

    def "test ssl2"() {
        given:
        TestKeyStore keyStore = TestKeyStore.init(resources.dir, "PKCS12")

        def props = System.getProperties()
        props.put("javax.net.ssl.keyStoreType", "PKCS12")
        props.put("javax.net.ssl.keyStore", keyStore.keyStore.absolutePath)
        props.put("javax.net.ssl.keyStorePassword", keyStore.keyStorePassword)
        props.put("javax.net.ssl.trustStoreType", "PKCS12")
        props.put("javax.net.ssl.trustStore", keyStore.keyStore.absolutePath)
        props.put("javax.net.ssl.trustStorePassword", keyStore.keyStorePassword)

        SslContextFactory factory = new DefaultSslContextFactory()

        HttpSettings settings = DefaultHttpSettings.builder()
            .withAuthenticationSettings([])
            .withRedirectVerifier {}
            .withSslContextFactory(factory)
            .build()

        HttpClientHelper client = new HttpClientHelper(new DocumentationRegistry(), settings)

        server.configure(keyStore)
        server.start()
        server.expect('/test')

        when:
        client.performGet("${server.getUri()}/test", false)

        then:
        noExceptionThrown()

        cleanup:
        client.close()
    }

    def "server that only supports current TLS versions"() {
        given:
        Security.addProvider(new FakeProvider());

        TestKeyStore keyStore = TestKeyStore.init(resources.dir, "FAKEKS")
        HttpSettings settings = DefaultHttpSettings.builder()
            .withAuthenticationSettings([])
            .withRedirectVerifier {}
            .withSslContextFactory { keyStore.asSSLContext() }
            .build()

        HttpClientHelper client = new HttpClientHelper(new DocumentationRegistry(), settings)
        // Only support modern TLS versions
        server.configure(keyStore)
        server.start()
        server.expect('/test')

        when:
        client.performGet("${server.getUri()}/test", false)

        then:
        noExceptionThrown()

        cleanup:
        client.close()
    }
}
