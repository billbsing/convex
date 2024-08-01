package convex.cli;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;

import convex.core.crypto.PFXTools;
import convex.core.util.Utils;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

public class StoreMixin {

	@Option(names = { "--keystore" }, 
			defaultValue = "${env:CONVEX_KEYSTORE:-" + Constants.KEYSTORE_FILENAME+ "}", 
			scope = ScopeType.INHERIT, 
			description = "Keystore filename. Default: ${DEFAULT-VALUE}")
	private String keyStoreFilename;

	/**
	 * Password for keystore. Option named to match Java keytool
	 */
	@Option(names = {"--storepass" }, 
			scope = ScopeType.INHERIT, 
			defaultValue = "${env:CONVEX_KEYSTORE_PASSWORD}", 
			description = "Password to read/write to the Keystore") 
	String keystorePassword;

	KeyStore keyStore = null;
	
	/**
	 * Gets the keystore file name currently used for the CLI
	 * 
	 * @return File name, or null if not specified
	 */
	public File getKeyStoreFile() {
		if (keyStoreFilename != null) {
			File f = Utils.getPath(keyStoreFilename);
			return f;
		}
		return null;
	}

	/**
	 * Get the currently configured password for the keystore. Will emit warning and
	 * default to blank password if not provided
	 * 
	 * @param main TODO
	 * @return Password string
	 */
	public char[] getStorePassword(Main main) {
		char[] storepass = null;
	
		if (keystorePassword != null) {
			storepass = keystorePassword.toCharArray();
		} else {
			if (!main.nonInteractive) {
				storepass = main.readPassword("Enter Keystore Password: ");
				keystorePassword=new String(storepass);
			}
	
			if (storepass == null) {
				main.paranoia("Keystore password must be explicitly provided");
				Main.log.warn("No password for keystore: defaulting to blank password");
				storepass = new char[0];
			}
		}
		return storepass;
	}

	/**
	 * Gets the current key store
	 * 
	 * @param main TODO
	 * @return KeyStore instance, or null if it does not exist
	 */
	public KeyStore getKeystore(Main main) {
		if (keyStore == null) {
			keyStore = loadKeyStore(false, getStorePassword(main));
		}
		return keyStore;
	}

	/**
	 * Loads the currently configured key Store
	 * 
	 * @param main TODO
	 * @return KeyStore instance, or null if does not exist
	 */
	public KeyStore loadKeyStore(Main main) {
		return main.storeMixin.loadKeyStore(false, getStorePassword(main));
	}

	/**
	 * Loads the currently configured key Store
	 * 
	 * @param isCreate Flag to indicate if keystore should be created if absent
	 * @param password TODO
	 * @return KeyStore instance, or null if does not exist
	 */
	public KeyStore loadKeyStore(boolean isCreate, char[] password) {
		File keyFile = getKeyStoreFile();
		try {
			if (keyFile.exists()) {
				keyStore = PFXTools.loadStore(keyFile, password);
			} else if (isCreate) {
				Main.log.debug("No keystore exists, creating at: " + keyFile.getCanonicalPath());
				Utils.ensurePath(keyFile);
				keyStore = PFXTools.createStore(keyFile, password);
			}
		} catch (FileNotFoundException e) {
			return null;
		} catch (GeneralSecurityException e) {
			throw new CLIError("Unexpected security error: " + e.getClass(), e);
		} catch (IOException e) {
			if (e.getCause() instanceof UnrecoverableKeyException) {
				throw new CLIError("Invalid password for keystore: " + keyFile);
			}
			throw new CLIError("Unable to read keystore at: " + keyFile, e);
		}
		return keyStore;
	}

	public void saveKeyStore(char[] storePassword) {
		// save the keystore file
		if (keyStore == null)
			throw new CLIError("Trying to save a keystore that has not been loaded!");
		try {
			PFXTools.saveStore(keyStore, getKeyStoreFile(), storePassword);
		} catch (Throwable t) {
			throw Utils.sneakyThrow(t);
		}
	}

}
