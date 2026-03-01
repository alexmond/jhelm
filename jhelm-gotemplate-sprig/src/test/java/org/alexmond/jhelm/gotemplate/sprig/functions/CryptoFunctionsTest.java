package org.alexmond.jhelm.gotemplate.sprig.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

class CryptoFunctionsTest {

	private String exec(String template) throws IOException, TemplateException {
		StringWriter writer = new StringWriter();
		GoTemplate t = new GoTemplate();
		t.parse("test", template);
		t.execute("test", new HashMap<>(), writer);
		return writer.toString();
	}

	// ========== Random String Functions ==========

	@Test
	void testRandAlphaNumLength() throws IOException, TemplateException {
		String result = exec("{{ randAlphaNum 10 }}");
		assertEquals(10, result.length());
		assertTrue(result.matches("[A-Za-z0-9]+"));
	}

	@Test
	void testRandAlphaLength() throws IOException, TemplateException {
		String result = exec("{{ randAlpha 8 }}");
		assertEquals(8, result.length());
		assertTrue(result.matches("[A-Za-z]+"));
	}

	@Test
	void testRandNumericLength() throws IOException, TemplateException {
		String result = exec("{{ randNumeric 6 }}");
		assertEquals(6, result.length());
		assertTrue(result.matches("[0-9]+"));
	}

	@Test
	void testRandAsciiLength() throws IOException, TemplateException {
		String result = exec("{{ randAscii 12 }}");
		assertEquals(12, result.length());
	}

	@Test
	void testRandAlphaNumZeroLength() throws IOException, TemplateException {
		String result = exec("{{ randAlphaNum 0 }}");
		assertEquals("", result);
	}

	// ========== Password Functions ==========

	@Test
	void testHtpasswd() throws IOException, TemplateException {
		String result = exec("{{ htpasswd \"admin\" \"secret\" }}");
		assertTrue(result.startsWith("admin:$2"));
	}

	@Test
	void testDerivePassword() throws IOException, TemplateException {
		String result = exec("{{ derivePassword 1 \"long\" \"masterpass\" \"user\" }}");
		assertNotNull(result);
		assertFalse(result.isEmpty());
		assertEquals(20, result.length());
	}

	@Test
	void testDerivePasswordDeterministic() throws IOException, TemplateException {
		String result1 = exec("{{ derivePassword 1 \"long\" \"master\" \"user1\" }}");
		String result2 = exec("{{ derivePassword 1 \"long\" \"master\" \"user1\" }}");
		assertEquals(result1, result2);
	}

	// ========== Key Generation ==========

	@Test
	void testGenPrivateKeyRsa() throws IOException, TemplateException {
		String result = exec("{{ genPrivateKey \"rsa\" }}");
		assertTrue(result.contains("BEGIN RSA PRIVATE KEY"));
		assertTrue(result.contains("END RSA PRIVATE KEY"));
	}

	@Test
	void testGenPrivateKeyEcdsa() throws IOException, TemplateException {
		String result = exec("{{ genPrivateKey \"ecdsa\" }}");
		assertTrue(result.contains("PRIVATE KEY"));
	}

	@Test
	void testGenPrivateKeyDsa() throws IOException, TemplateException {
		String result = exec("{{ genPrivateKey \"dsa\" }}");
		assertTrue(result.contains("PRIVATE KEY"));
	}

	// ========== Certificate Generation ==========

	@Test
	void testGenCA() throws IOException, TemplateException {
		String result = exec("{{ $ca := genCA \"myCA\" 365 }}{{ $ca.Cert }}");
		assertTrue(result.contains("BEGIN CERTIFICATE"));
		assertTrue(result.contains("END CERTIFICATE"));
	}

	@Test
	void testGenCAKey() throws IOException, TemplateException {
		String result = exec("{{ $ca := genCA \"myCA\" 365 }}{{ $ca.Key }}");
		assertTrue(result.contains("BEGIN RSA PRIVATE KEY"));
	}

	@Test
	void testGenSelfSignedCert() throws IOException, TemplateException {
		StringWriter writer = new StringWriter();
		GoTemplate t = new GoTemplate();
		Map<String, Object> data = new HashMap<>();
		t.parse("test",
				"{{ $cert := genSelfSignedCert \"localhost\" (list \"127.0.0.1\") (list \"localhost\") 365 }}{{ $cert.Cert }}");
		t.execute("test", data, writer);
		assertTrue(writer.toString().contains("BEGIN CERTIFICATE"));
	}

	@Test
	void testGenSignedCert() throws IOException, TemplateException {
		StringWriter writer = new StringWriter();
		GoTemplate t = new GoTemplate();
		Map<String, Object> data = new HashMap<>();
		t.parse("test",
				"{{ $ca := genCA \"myCA\" 365 }}{{ $cert := genSignedCert \"myhost\" (list) (list \"myhost.local\") 365 $ca }}{{ $cert.Cert }}");
		t.execute("test", data, writer);
		assertTrue(writer.toString().contains("BEGIN CERTIFICATE"));
	}

	// ========== UUID ==========

	@Test
	void testUuidv4() throws IOException, TemplateException {
		String result = exec("{{ uuidv4 }}");
		assertNotNull(result);
		assertEquals(36, result.length());
		assertTrue(result.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
	}

	// --- New functions: bcrypt, randBytes, encryptAES/decryptAES, *WithKey ---

	@Test
	void testBcrypt() throws IOException, TemplateException {
		String result = exec("{{ bcrypt \"mysecret\" }}");
		assertTrue(result.startsWith("$2"));
		assertTrue(result.length() > 50);
	}

	@Test
	void testRandBytes() throws IOException, TemplateException {
		String result = exec("{{ randBytes 16 }}");
		assertNotNull(result);
		assertFalse(result.isEmpty());
		// Base64-encoded 16 bytes = 24 chars
		assertEquals(24, result.length());
	}

	@Test
	void testEncryptDecryptAES() throws IOException, TemplateException {
		// We can't test round-trip in a single template since each execution
		// generates a random IV, so test them separately
		String encrypted = exec("{{ encryptAES \"mySecretPassword12345678\" \"hello world\" }}");
		assertNotNull(encrypted);
		assertFalse(encrypted.isEmpty());

		// Decrypt the encrypted value
		Map<String, Object> data = new HashMap<>();
		data.put("encrypted", encrypted);
		StringWriter writer = new StringWriter();
		GoTemplate t = new GoTemplate();
		t.parse("test", "{{ decryptAES \"mySecretPassword12345678\" .encrypted }}");
		t.execute("test", data, writer);
		assertEquals("hello world", writer.toString());
	}

	@Test
	void testGenCAWithKey() throws IOException, TemplateException {
		String result = exec("{{ $ca := genCAWithKey \"myCA\" 365 \"\" }}{{ $ca.Cert }}");
		assertTrue(result.contains("BEGIN CERTIFICATE"));
	}

	@Test
	void testGenSelfSignedCertWithKey() throws IOException, TemplateException {
		StringWriter writer = new StringWriter();
		GoTemplate t = new GoTemplate();
		t.parse("test",
				"{{ $cert := genSelfSignedCertWithKey \"localhost\" (list) (list \"localhost\") 365 \"\" }}{{ $cert.Cert }}");
		t.execute("test", new HashMap<>(), writer);
		assertTrue(writer.toString().contains("BEGIN CERTIFICATE"));
	}

	@Test
	void testGenSignedCertWithKey() throws IOException, TemplateException {
		StringWriter writer = new StringWriter();
		GoTemplate t = new GoTemplate();
		t.parse("test",
				"{{ $ca := genCA \"myCA\" 365 }}{{ $cert := genSignedCertWithKey \"myhost\" (list) (list \"myhost\") 365 $ca \"\" }}{{ $cert.Cert }}");
		t.execute("test", new HashMap<>(), writer);
		assertTrue(writer.toString().contains("BEGIN CERTIFICATE"));
	}

}
