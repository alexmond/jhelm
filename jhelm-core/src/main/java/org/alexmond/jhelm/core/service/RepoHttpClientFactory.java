package org.alexmond.jhelm.core.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

import org.alexmond.jhelm.core.model.RepositoryConfig;

/**
 * Builds HTTP clients with TLS and Basic Auth for authenticated Helm repositories.
 */
final class RepoHttpClientFactory {

	private final CloseableHttpClient defaultClient;

	private final boolean globalInsecureSkipTls;

	RepoHttpClientFactory(CloseableHttpClient defaultClient, boolean globalInsecureSkipTls) {
		this.defaultClient = defaultClient;
		this.globalInsecureSkipTls = globalInsecureSkipTls;
	}

	void configureAuth(HttpGet request, RepositoryConfig.Repository repo) {
		if (repo == null) {
			return;
		}
		if (repo.getUsername() != null && !repo.getUsername().isEmpty()) {
			String password = (repo.getPassword() != null) ? repo.getPassword() : "";
			String credentials = repo.getUsername() + ":" + password;
			String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
			request.setHeader("Authorization", "Basic " + encoded);
		}
	}

	<T> T executeGet(HttpGet request, RepositoryConfig.Repository repo, HttpClientResponseHandler<T> handler)
			throws IOException {
		configureAuth(request, repo);
		if (needsCustomTls(repo)) {
			try (CloseableHttpClient client = buildTlsClient(repo)) {
				return client.execute(request, handler);
			}
		}
		return defaultClient.execute(request, handler);
	}

	private boolean needsCustomTls(RepositoryConfig.Repository repo) {
		if (repo == null) {
			return false;
		}
		boolean hasTls = (repo.getCertFile() != null && !repo.getCertFile().isEmpty())
				|| (repo.getCaFile() != null && !repo.getCaFile().isEmpty());
		boolean skipVerify = repo.isInsecure_skip_tls_verify() || globalInsecureSkipTls;
		return hasTls || skipVerify;
	}

	private CloseableHttpClient buildTlsClient(RepositoryConfig.Repository repo) throws IOException {
		try {
			boolean skipVerify = repo.isInsecure_skip_tls_verify() || globalInsecureSkipTls;
			SSLContext sslContext = buildSslContext(repo, skipVerify);
			var socketFactoryBuilder = SSLConnectionSocketFactoryBuilder.create().setSslContext(sslContext);
			if (skipVerify) {
				socketFactoryBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
			}
			HttpClientConnectionManager connManager = PoolingHttpClientConnectionManagerBuilder.create()
				.setSSLSocketFactory(socketFactoryBuilder.build())
				.build();
			return HttpClients.custom().setConnectionManager(connManager).build();
		}
		catch (Exception ex) {
			throw new IOException("Failed to build TLS-configured HTTP client", ex);
		}
	}

	private SSLContext buildSslContext(RepositoryConfig.Repository repo, boolean skipVerify) throws Exception {
		KeyManagerFactory kmf = null;
		TrustManagerFactory tmf = null;

		if (repo.getCertFile() != null && !repo.getCertFile().isEmpty() && repo.getKeyFile() != null
				&& !repo.getKeyFile().isEmpty()) {
			KeyStore keyStore = KeyStore.getInstance("PKCS12");
			keyStore.load(null, null);
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			Certificate cert;
			try (InputStream certIn = new FileInputStream(repo.getCertFile())) {
				cert = cf.generateCertificate(certIn);
			}
			PrivateKey key = loadPrivateKey(repo.getKeyFile());
			keyStore.setKeyEntry("client", key, new char[0], new Certificate[] { cert });
			kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(keyStore, new char[0]);
		}

		if (repo.getCaFile() != null && !repo.getCaFile().isEmpty()) {
			KeyStore trustStore = KeyStore.getInstance("PKCS12");
			trustStore.load(null, null);
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			try (InputStream caIn = new FileInputStream(repo.getCaFile())) {
				int idx = 0;
				for (Certificate c : cf.generateCertificates(caIn)) {
					trustStore.setCertificateEntry("ca-" + idx++, c);
				}
			}
			tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(trustStore);
		}

		SSLContext sslContext = SSLContext.getInstance("TLS");
		TrustManager[] trustManagers = null;
		if (skipVerify) {
			trustManagers = new TrustManager[] { new InsecureTrustManager() };
		}
		else if (tmf != null) {
			trustManagers = tmf.getTrustManagers();
		}
		sslContext.init((kmf != null) ? kmf.getKeyManagers() : null, trustManagers, null);
		return sslContext;
	}

	private PrivateKey loadPrivateKey(String keyFilePath) throws Exception {
		String pem = Files.readString(Paths.get(keyFilePath));
		String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
			.replace("-----END PRIVATE KEY-----", "")
			.replace("-----BEGIN RSA PRIVATE KEY-----", "")
			.replace("-----END RSA PRIVATE KEY-----", "")
			.replaceAll("\\s", "");
		byte[] decoded = Base64.getDecoder().decode(base64);
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		return kf.generatePrivate(spec);
	}

	private static final class InsecureTrustManager implements X509TrustManager {

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {
			// intentionally empty — skip verification
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {
			// intentionally empty — skip verification
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}

	}

}
