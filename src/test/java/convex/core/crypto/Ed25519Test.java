package convex.core.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import convex.core.Constants;
import convex.core.data.Address;
import convex.core.data.SignedData;
import convex.core.exceptions.BadFormatException;
import convex.core.exceptions.InvalidDataException;

public class Ed25519Test {
	
	@Test
	public void testKeyGen() {
		AKeyPair kp1=Ed25519KeyPair.generate();
		AKeyPair kp2=Ed25519KeyPair.generate();
		assertNotEquals(kp1,kp2);
	}
	
	@Test
	public void testPublicKeyBytes() {
		Ed25519KeyPair kp1=Ed25519KeyPair.generate();
		byte[] publicBytes=kp1.getPublicKeyBytes(); 
		byte[] addressBytes=kp1.getAddress().getBytes();
		assertArrayEquals(publicBytes,addressBytes);
	}
	
	@Test
	public void testPrivateKeyBytes() {
		Ed25519KeyPair kp1=Ed25519KeyPair.generate();
		SignedData<Long> sd1=kp1.signData(1L);
		assertTrue(sd1.isValid());
		
		byte[] privateKeyBytes=kp1.getPrivate().getEncoded();
		

		Ed25519KeyPair kp2=Ed25519KeyPair.create(kp1.getPublic(),kp1.getPrivate());
		assertEquals(kp1.getAddress(),kp2.getAddress());
		assertArrayEquals(privateKeyBytes,kp2.getPrivate().getEncoded());
		
		SignedData<Long> sd2=kp2.signData(1L);
		assertTrue(sd2.isValid());
	}
	
	@Test
	public void testSeededKeyGen() {
		AKeyPair kp1=Ed25519KeyPair.createSeeded(1337);
		AKeyPair kp2=Ed25519KeyPair.createSeeded(1337);
		AKeyPair kp3=Ed25519KeyPair.createSeeded(13378);
		assertEquals(kp1,kp2);
		assertNotEquals(kp2,kp3);
	}
	
	@Test
	public void testSigFromHex() throws BadFormatException, InvalidDataException {
		String s="cafebabe000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
		ASignature s1=ASignature.fromHex(s);
		s1.validate();
		
		assertEquals(s,s1.toHexString());
		
		assertThrows(IllegalArgumentException.class,()->ASignature.fromHex("00"));
	}
	
	@Test
	public void testAddressRoundTrip() {
		// run only if we are using Ed25519 Addresses
		assumeTrue(Constants.USE_ED25519);
		
		// Address should round trip to a Ed25519 public key and back again
		Address a=Address.fromHex("0123456701234567012345670123456701234567012345670123456701234567");
		PublicKey pk=Ed25519KeyPair.publicKeyFromBytes(a.getBytes());
		Address b=Ed25519KeyPair.extractAddress(pk);
		assertEquals(a,b);
	}
	
	/**
	 * Example test values from: https://stackoverflow.com/questions/53921655/rebuild-of-ed25519-keys-with-bouncy-castle-java
	 * @throws NoSuchAlgorithmException 
	 * @throws IOException 
	 * @throws InvalidKeySpecException 
	 * @throws InvalidKeyException 
	 * @throws SignatureException 
	 */
	@Test 
	public void testExample() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, InvalidKeyException, SignatureException {
		
		byte [] msg = "eyJhbGciOiJFZERTQSJ9.RXhhbXBsZSBvZiBFZDI1NTE5IHNpZ25pbmc".getBytes(StandardCharsets.UTF_8);

        byte[] privateKeyBytes = Base64.getUrlDecoder().decode("nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A");
        byte[] publicKeyBytes = Base64.getUrlDecoder().decode("11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo");
        assertEquals(32,privateKeyBytes.length);
        assertEquals(32,publicKeyBytes.length);
        
        PublicKey publicKey=Ed25519KeyPair.publicKeyFromBytes(publicKeyBytes);
        PrivateKey privateKey=Ed25519KeyPair.privateKeyFromBytes(privateKeyBytes);
       
        // Sign
        Signature signer = Signature.getInstance("EdDSA");
        signer.initSign(privateKey);
        signer.update(msg, 0, msg.length);
        byte[] signature = signer.sign();
        
        String sigText=Base64.getUrlEncoder().encodeToString(signature).replace("=", "");
        assertEquals("hgyY0il_MGCjP0JzlnLWG1PPOt7-09PGcvMg3AIbQR6dWbhijcNR4ki4iylGjg5BhVsPt9g7sVvpAr_MuM0KAg",sigText);
        
        // Verify
        Signature verifier = Signature.getInstance("EdDSA");
        verifier.initVerify(publicKey);
        verifier.update(msg);
        assertTrue(verifier.verify(signature));
        
        // Bad verify - wrong signature
        verifier.initVerify(publicKey);
        verifier.update(msg);
        assertFalse(verifier.verify(new byte[64]));
	}
	
	
}
