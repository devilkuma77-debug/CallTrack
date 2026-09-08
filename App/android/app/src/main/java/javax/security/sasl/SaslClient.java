package javax.security.sasl;

/**
 * Minimal SASL client interface for MongoDB Java driver on Android
 * (Android SDK does not ship javax.security.sasl).
 */
public interface SaslClient {
    String getMechanismName();

    boolean hasInitialResponse() throws SaslException;

    byte[] evaluateChallenge(byte[] challenge) throws SaslException;

    boolean isComplete();

    byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException;

    byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException;

    Object getNegotiatedProperty(String propName);

    void dispose() throws SaslException;
}
